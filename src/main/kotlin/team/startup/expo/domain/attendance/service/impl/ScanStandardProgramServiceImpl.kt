package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResult
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.ProgramEntryWindow
import team.startup.expo.domain.attendance.service.ProgramScanNameResolver
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    @Qualifier("applicationCircuitBreaker") private val applicationCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val recordProgramAttendanceService: RecordProgramAttendanceService,
    private val cancellationGuardGate: CancellationGuardGate,
    private val properties: ProgramAttendanceProperties,
    private val programScanNameResolver: ProgramScanNameResolver,
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
    ): ProgramScanResDto {
        checkParticipant(reqDto)
        val program =
            expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.")) {
                expoClient.getStandardProgram(reqDto.expoId, programId)
            }
        expoPeriodValidator.checkInProgress(reqDto.expoId)

        // 입실만 일정 시간대 안에서 받는다. 이미 입실한 사람의 퇴실은 시각을 보지 않는다(#198)
        val entryAllowed =
            ProgramEntryWindow.allowsEntry(program.startedAt, program.endedAt, clock.instant(), properties.leadMinutes)
        // 분 단위로 기록한다(v1과 같음)
        val now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        val attendanceDate = LocalDate.now(clock)
        // 신청 확인과 기록을 같은 취소 보호 락 안에서 한다. 이미 락 안에 있는 스캔은 신청 취소(acquire)가 기다리고, 취소가 끝난 뒤 들어온
        // 스캔은 최신 신청 상태를 읽는다(#88).
        val result =
            cancellationGuardGate.scanning(reqDto.expoId, participantId = reqDto.participantId, attendanceDate = attendanceDate) {
                val applied =
                    applicationCircuitBreaker.callService(
                        "신청",
                    ) { applicationClient.checkStandardApplication(programId, reqDto.participantId) }
                if (!applied.applied) {
                    throw ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 참가 중인 유저를 찾지 못 했습니다.")
                }
                recordProgramAttendanceService.record(
                    ProgramType.STANDARD,
                    programId,
                    reqDto.participantId,
                    attendanceDate,
                    now,
                    entryAllowed,
                )
            }
        val scanResult =
            when (result) {
                ProgramAttendanceResult.ENTERED -> ProgramScanResult.ENTERED
                ProgramAttendanceResult.LEFT -> ProgramScanResult.LEFT
                ProgramAttendanceResult.ALREADY_ENTERED -> ProgramScanResult.UNCHANGED
                ProgramAttendanceResult.PROGRAM_DELETED -> throw ExpectedException(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.")
                ProgramAttendanceResult.ENTRY_NOT_ALLOWED -> throw ExpectedException(HttpStatus.BAD_REQUEST, "출석 가능한 시간이 아닙니다.")
            }
        return ProgramScanResDto(
            result = scanResult,
            name = programScanNameResolver.standard(reqDto.expoId, reqDto.participantId),
            time = if (scanResult == ProgramScanResult.UNCHANGED) null else now.format(TIME),
        )
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
