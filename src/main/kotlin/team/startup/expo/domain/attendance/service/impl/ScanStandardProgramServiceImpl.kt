package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import team.startup.expo.domain.attendance.service.ScanStandardProgramService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.config.ProgramAttendanceProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * v1 `ScanStandardProByQrCodeServiceImpl`의 일반 프로그램 출석. 첫 스캔은 입실을 기록하고, 이미 입실한 사람이
 * 다시 찍어도 에러 없이 성공하며 아무것도 바꾸지 않는다(퇴실은 기록하지 않는다). 다른 서비스를 부르는 동안
 * 트랜잭션을 잡지 않도록 이 서비스에는 `@Transactional`을 두지 않고, 기록은 조건부 SQL이라 동시 스캔에도 안전하다.
 */
@Service
class ScanStandardProgramServiceImpl(
    private val userClient: UserClient,
    private val expoClient: ExpoClient,
    private val applicationClient: ApplicationClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    @Qualifier("applicationCircuitBreaker") private val applicationCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val recordProgramAttendanceService: RecordProgramAttendanceService,
    private val properties: ProgramAttendanceProperties,
    private val clock: Clock,
) : ScanStandardProgramService {
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

    override fun scan(
        programId: Long,
        reqDto: ScanStandardProgramReqDto,
    ) {
        checkParticipant(reqDto)
        expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.")) {
            expoClient.getStandardProgram(reqDto.expoId, programId)
        }
        expoPeriodValidator.checkInProgress(reqDto.expoId)

        val applied =
            applicationCircuitBreaker.callService(
                "신청",
            ) { applicationClient.checkStandardApplication(programId, reqDto.participantId) }
        if (!applied.applied) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 참가 중인 유저를 찾지 못 했습니다.")
        }

        // 분 단위로 기록한다(v1과 같음)
        val now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        when (recordProgramAttendanceService.record(ProgramType.STANDARD, programId, reqDto.participantId, LocalDate.now(clock), now)) {
            ProgramAttendanceResult.ENTERED, ProgramAttendanceResult.ALREADY_ENTERED -> Unit
            ProgramAttendanceResult.PROGRAM_DELETED -> throw ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.")
        }
    }
}
