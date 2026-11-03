package team.startup.expo.domain.attendance.support

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * 입장·프로그램 스캔이 취소 보호와 겹치지 않게 하는 관문이다. 스캔은 참가자(연수는 연수자) 단위 공유 락을 잡은 채 취소 보호를 확인하고
 * 기록을 마친다. 취소 검사는 같은 키의 배타 락을 잡아 진행 중인 스캔이 끝난 뒤에 보호를 걸고, 그 뒤의 스캔은 거절된다.
 * 서로 다른 사람의 스캔은 막지 않는다(동시에 들어오는 수만 [ScanConcurrencyLimiter]가 커넥션 풀 크기 아래로 제한한다, #247). 락을 기다리는 시간과 원격 호출 시간에 상한이 있어 DB 커넥션을 오래 쥐지 않는다.
 *
 * [scanning]은 같은 트랜잭션에서 차례로 확인한 뒤 블록을 실행한다.
 * 1. 참가자·연수자 취소 보호([checkGuard]): HELD면 409, COMMITTED는 그 날짜의 ACTIVE FIELD가 없으면 400이다.
 * 2. 날짜별 현장 방문 취소 보류(`fieldVisits.held`, #204): 그 날짜의 참가자나 연결 연수자에 HELD 보호가 있으면 409다.
 * 3. 오늘 입장 자격(`preregisterSessionValidator.checkToday`): 그 날짜의 ACTIVE FIELD나 ACTIVE PRE 회차가 없으면 400이다.
 *
 * 다른 서비스를 부르는 동안 트랜잭션을 잡지 않는다는 이 서비스의 원칙의 예외이고, 이 관문 안에서만 그렇게 한다(#79).
 */
@Component
class CancellationGuardGate(
    transactionManager: PlatformTransactionManager,
    private val cancellationGuardRepository: CancellationGuardRepository,
    private val fieldVisits: FieldVisitRepository,
    private val preregisterSessionValidator: PreregisterSessionValidator,
    private val clock: Clock,
    private val scanLimiter: ScanConcurrencyLimiter,
) {
    private val lockExecutor = GuardLockExecutor(transactionManager, cancellationGuardRepository)

    fun <T> scanning(
        expoId: String,
        participantId: Long? = null,
        traineeId: Long? = null,
        attendanceDate: LocalDate? = null,
        block: () -> T,
    ): T = scanLimiter.limited { scanningInTransaction(expoId, participantId, traineeId, attendanceDate, block) }

    private fun <T> scanningInTransaction(
        expoId: String,
        participantId: Long?,
        traineeId: Long?,
        attendanceDate: LocalDate?,
        block: () -> T,
    ): T {
        val date = attendanceDate ?: LocalDate.now(clock)
        return lockExecutor.execute("취소 처리와 겹쳐 잠시 후 다시 시도해 주세요.") {
            if (participantId != null) {
                checkGuard(
                    expoId,
                    participantKey(expoId, participantId),
                    date,
                    findGuard = { cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId) },
                    guardedParticipantId = { participantId },
                )
            }
            if (traineeId != null) {
                checkGuard(
                    expoId,
                    traineeKey(expoId, traineeId),
                    date,
                    findGuard = { cancellationGuardRepository.findByExpoIdAndTraineeId(expoId, traineeId) },
                    guardedParticipantId = { it.participantId },
                )
            }
            checkFieldVisitHold(expoId, participantId, traineeId, date)
            checkTodaySessions(expoId, participantId, traineeId, date)
            block()
        }
    }

    /**
     * [lockKey]의 공유 락을 잡은 뒤 취소 보호를 확인한다. 보호가 HELD이면 거절하고, COMMITTED여도 [date]의 ACTIVE FIELD가 없으면 거절한다.
     * FIELD 확인 대상은 [guardedParticipantId]가 정한다(참가자 스캔은 그 참가자, 연수자 스캔은 보호를 건 참가자).
     */
    private fun checkGuard(
        expoId: String,
        lockKey: String,
        date: LocalDate,
        findGuard: () -> CancellationGuard?,
        guardedParticipantId: (CancellationGuard) -> Long,
    ) {
        lockExecutor.lockShared(lockKey, GuardLockExecutor.WAIT_SCAN)
        findGuard()?.let {
            if (it.state == CancellationGuardState.HELD ||
                preregisterSessionValidator.activeField(expoId, guardedParticipantId(it), date) == null
            ) {
                reject(it)
            }
        }
    }

    /** 날짜별 현장 방문 취소(#204)가 [date]의 참가자나 연결 연수자에 보호를 걸어 두었으면 거절한다. */
    private fun checkFieldVisitHold(
        expoId: String,
        participantId: Long?,
        traineeId: Long?,
        date: LocalDate,
    ) {
        if (fieldVisits.held(expoId, participantId, traineeId, date)) {
            throw ExpectedException(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.")
        }
    }

    /**
     * [date]의 입장 자격(그날 ACTIVE FIELD나 그날 시작하는 ACTIVE PRE 회차)을 확인하고, 없으면 400 "취소된 신청입니다."다(#204).
     * 참가자 스캔은 그 참가자를, 참가자 없이 연수자만 스캔하면 그 날짜의 현장 방문 보호에 연결된 참가자들을 확인한다.
     */
    private fun checkTodaySessions(
        expoId: String,
        participantId: Long?,
        traineeId: Long?,
        date: LocalDate,
    ) {
        if (participantId != null) preregisterSessionValidator.checkToday(expoId, participantId, date)
        if (participantId == null && traineeId != null) {
            fieldVisits.linkedParticipants(expoId, traineeId, date).forEach {
                preregisterSessionValidator.checkToday(expoId, it, date)
            }
        }
    }

    private fun reject(guard: CancellationGuard): Nothing =
        when (guard.state) {
            CancellationGuardState.HELD -> throw ExpectedException(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.")
            CancellationGuardState.COMMITTED -> throw ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")
        }

    companion object {
        const val LOCK_TIMEOUT = "5s"

        fun participantKey(
            expoId: String,
            participantId: Long,
        ) = "guard:$expoId:p:$participantId"

        fun traineeKey(
            expoId: String,
            traineeId: Long,
        ) = "guard:$expoId:t:$traineeId"

        /** 날짜별 현장 방문 등록 하나(#204)의 배타 락 키. 참가자 락 다음에 잡는다. */
        fun fieldVisitKey(registrationId: UUID) = "field-visit:$registrationId"
    }
}
