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
 * 서로 다른 사람의 스캔은 막지 않는다. 락을 기다리는 시간과 원격 호출 시간에 상한이 있어 DB 커넥션을 오래 쥐지 않는다.
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
) {
    private val lockExecutor = GuardLockExecutor(transactionManager, cancellationGuardRepository)

    fun <T> scanning(
        expoId: String,
        participantId: Long? = null,
        traineeId: Long? = null,
        attendanceDate: LocalDate? = null,
        block: () -> T,
    ): T {
        val date = attendanceDate ?: LocalDate.now(clock)
        return lockExecutor.execute("취소 처리와 겹쳐 잠시 후 다시 시도해 주세요.") {
            if (participantId != null) {
                lockExecutor.lockShared(participantKey(expoId, participantId), GuardLockExecutor.WAIT_SCAN)
                cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)?.let {
                    if (it.state == CancellationGuardState.HELD ||
                        preregisterSessionValidator.activeField(expoId, participantId, date) == null
                    ) {
                        reject(it)
                    }
                }
            }
            if (traineeId != null) {
                lockExecutor.lockShared(traineeKey(expoId, traineeId), GuardLockExecutor.WAIT_SCAN)
                cancellationGuardRepository.findByExpoIdAndTraineeId(expoId, traineeId)?.let {
                    if (it.state == CancellationGuardState.HELD ||
                        preregisterSessionValidator.activeField(expoId, it.participantId, date) == null
                    ) {
                        reject(it)
                    }
                }
            }
            if (fieldVisits.held(expoId, participantId, traineeId, date)) {
                throw ExpectedException(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.")
            }
            if (participantId != null) preregisterSessionValidator.checkToday(expoId, participantId, date)
            if (participantId == null && traineeId != null) {
                fieldVisits.linkedParticipants(expoId, traineeId, date).forEach {
                    preregisterSessionValidator.checkToday(expoId, it, date)
                }
            }
            block()
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
