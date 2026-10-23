package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramEntryWindow
import team.startup.expo.domain.attendance.service.ProgramScanNameResolver
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import team.startup.expo.domain.attendance.service.ScanStandardProgramService
import team.startup.expo.domain.attendance.support.AbstractProgramScanService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.config.ProgramAttendanceProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock

/**
 * v1 `ScanStandardProByQrCodeServiceImpl`의 일반 프로그램 출석. 첫 스캔은 입실, 그 뒤 첫 스캔은 퇴실을 기록하고
 * 그 밖의 재스캔(같은 분 안의 연속 스캔, 퇴실 뒤)은 에러 없이 성공하며 아무것도 바꾸지 않는다. 다른 서비스를 부르는 동안
 * 트랜잭션을 잡지 않도록 이 서비스에는 `@Transactional`을 두지 않고, 기록은 조건부 SQL이라 동시 스캔에도 안전하다.
 */
@Service
class ScanStandardProgramServiceImpl(
    private val userClient: UserClient,
    private val expoClient: ExpoClient,
    private val applicationClient: ApplicationClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    @Qualifier("expoCircuitBreaker") expoCircuitBreaker: CircuitBreaker,
    @Qualifier("applicationCircuitBreaker") applicationCircuitBreaker: CircuitBreaker,
    expoPeriodValidator: ExpoPeriodValidator,
    recordProgramAttendanceService: RecordProgramAttendanceService,
    cancellationGuardGate: CancellationGuardGate,
    private val properties: ProgramAttendanceProperties,
    private val programScanNameResolver: ProgramScanNameResolver,
    clock: Clock,
) : AbstractProgramScanService<StandardProgramResDto>(
        expoCircuitBreaker,
        applicationCircuitBreaker,
        expoPeriodValidator,
        recordProgramAttendanceService,
        cancellationGuardGate,
        clock,
    ),
    ScanStandardProgramService {
    /**
     * `code`가 있으면 참가자 ID와 함께 유저 서비스에서 확인한다(없는 참가자, 다른 박람회, 틀린 code는 같은 404).
     * 없으면 설정에 따라 거부하거나 이전 앱을 위해 참가자 ID만으로 존재만 확인한다.
     */
    private fun checkParticipant(reqDto: ScanStandardProgramReqDto) {
        val notFound = ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        val code = reqDto.code
        if (!code.isNullOrBlank()) {
            userCircuitBreaker.callService("유저", notFound) {
                userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(reqDto.expoId, reqDto.participantId, code))
            }
            return
        }
        if (properties.requireCode) throw ExpectedException(HttpStatus.BAD_REQUEST, "참가자 코드가 필요합니다.")
        userCircuitBreaker.callService("유저", notFound) {
            userClient.getStandardParticipantNames(StandardParticipantNamesReqDto(reqDto.expoId, listOf(reqDto.participantId)))
        }
    }

    override val programType = ProgramType.STANDARD
    override val programNotFoundMessage = "일반 프로그램을 찾지 못 했습니다."
    override val notAppliedMessage = "일반 프로그램을 참가 중인 유저를 찾지 못 했습니다."

    override fun scan(
        programId: Long,
        reqDto: ScanStandardProgramReqDto,
    ): ProgramScanResDto {
        checkParticipant(reqDto)
        return scanProgram(reqDto.expoId, programId, reqDto.participantId, participantId = reqDto.participantId)
    }

    override fun findProgram(
        expoId: String,
        programId: Long,
    ) = expoClient.getStandardProgram(expoId, programId)

    override fun isApplied(
        programId: Long,
        personId: Long,
    ) = applicationClient.checkStandardApplication(programId, personId).applied

    override fun resolveName(
        expoId: String,
        personId: Long,
    ) = programScanNameResolver.standard(expoId, personId)

    // 입실만 일정 시간대 안에서 받는다. 이미 입실한 사람의 퇴실은 시각을 보지 않는다(#198)
    override fun allowsEntry(program: StandardProgramResDto) =
        ProgramEntryWindow.allowsEntry(program.startedAt, program.endedAt, clock.instant(), properties.leadMinutes)
}
