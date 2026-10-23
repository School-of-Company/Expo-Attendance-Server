package team.startup.expo.domain.attendance.support

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResult
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.attendance.support.ProgramAttendanceResult
import team.startup.expo.domain.attendance.support.RecordProgramAttendanceService
import team.startup.expo.global.client.callService
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 일반·연수 프로그램 스캔의 공통 흐름. 하위 서비스는 스캔한 사람을 확인한 뒤 [scanProgram]을 부르고, 프로그램 조회와 신청 확인,
 * 오류 문구, 응답에 담을 이름만 정한다. 다른 서비스를 부르는 동안 트랜잭션을 잡지 않도록 `@Transactional`을 두지 않는다.
 *
 * @param P [findProgram]이 돌려주는 프로그램 정보
 */
abstract class AbstractProgramScanService<P>(
    private val expoCircuitBreaker: CircuitBreaker,
    private val applicationCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val recordProgramAttendanceService: RecordProgramAttendanceService,
    private val cancellationGuardGate: CancellationGuardGate,
    protected val clock: Clock,
) {
    protected abstract val programType: ProgramType

    /** 프로그램이 없거나 삭제됐을 때의 404 문구. */
    protected abstract val programNotFoundMessage: String

    /** 신청하지 않은 사람의 404 문구. */
    protected abstract val notAppliedMessage: String

    /** 박람회 서비스에서 프로그램을 읽는다. 호출은 박람회 서킷 브레이커 안에서 이뤄진다. */
    protected abstract fun findProgram(
        expoId: String,
        programId: Long,
    ): P

    /** [personId]가 [programId]를 신청했는지 신청 서비스에 확인한다. 호출은 신청 서킷 브레이커 안에서 이뤄진다. */
    protected abstract fun isApplied(
        programId: Long,
        personId: Long,
    ): Boolean

    /** 응답에 담을 이름. 읽지 못하면 `null`. */
    protected abstract fun resolveName(
        expoId: String,
        personId: Long,
    ): String?

    /** 지금 입실을 받을 수 있는지. 기본은 항상 받는다. */
    protected open fun allowsEntry(program: P): Boolean = true

    /**
     * @param personId 출석을 기록할 대상(일반은 참가자 ID, 연수는 연수자 ID)
     * @param participantId 취소 보호 락을 잡을 참가자 ID
     * @param traineeId 취소 보호 락을 잡을 연수자 ID
     */
    protected fun scanProgram(
        expoId: String,
        programId: Long,
        personId: Long,
        participantId: Long? = null,
        traineeId: Long? = null,
    ): ProgramScanResDto {
        val notFound = ExpectedException(HttpStatus.NOT_FOUND, programNotFoundMessage)
        val program = expoCircuitBreaker.callService("박람회", notFound) { findProgram(expoId, programId) }
        expoPeriodValidator.checkInProgress(expoId)

        val entryAllowed = allowsEntry(program)
        // 분 단위로 기록한다(v1과 같음)
        val now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        val attendanceDate = LocalDate.now(clock)
        // 신청 확인과 기록을 같은 취소 보호 락 안에서 한다. 이미 락 안에 있는 스캔은 신청 취소(acquire)가 기다리고, 취소가 끝난 뒤 들어온
        // 스캔은 최신 신청 상태를 읽는다(#88).
        val result =
            cancellationGuardGate.scanning(
                expoId,
                participantId = participantId,
                traineeId = traineeId,
                attendanceDate = attendanceDate,
            ) {
                if (!applicationCircuitBreaker.callService("신청") { isApplied(programId, personId) }) {
                    throw ExpectedException(HttpStatus.NOT_FOUND, notAppliedMessage)
                }
                recordProgramAttendanceService.record(programType, programId, personId, attendanceDate, now, entryAllowed)
            }
        val scanResult =
            when (result) {
                ProgramAttendanceResult.ENTERED -> ProgramScanResult.ENTERED
                ProgramAttendanceResult.LEFT -> ProgramScanResult.LEFT
                ProgramAttendanceResult.ALREADY_ENTERED -> ProgramScanResult.UNCHANGED
                ProgramAttendanceResult.PROGRAM_DELETED -> throw ExpectedException(HttpStatus.NOT_FOUND, programNotFoundMessage)
                ProgramAttendanceResult.ENTRY_NOT_ALLOWED -> throw ExpectedException(HttpStatus.BAD_REQUEST, "출석 가능한 시간이 아닙니다.")
            }
        return ProgramScanResDto(
            result = scanResult,
            name = resolveName(expoId, personId),
            time = if (scanResult == ProgramScanResult.UNCHANGED) null else now.format(TIME),
        )
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
