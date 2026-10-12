package team.startup.expo.domain.attendance.service

import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics

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
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun <T> scanning(
        expoId: String,
        participantId: Long? = null,
        traineeId: Long? = null,
        block: () -> T,
    ): T {
        try {
            return requireNotNull(
                transaction.execute {
                    cancellationGuardRepository.setLockTimeout(LOCK_TIMEOUT)
                    if (participantId != null) {
                        timedLock { cancellationGuardRepository.lockShared(participantKey(expoId, participantId)) }
                        cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)?.let { reject(it) }
                    }
                    if (traineeId != null) {
                        timedLock { cancellationGuardRepository.lockShared(traineeKey(expoId, traineeId)) }
                        cancellationGuardRepository.findByExpoIdAndTraineeId(expoId, traineeId)?.let { reject(it) }
                    }
                    block()
                },
            )
        } catch (e: CannotAcquireLockException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "취소 처리와 겹쳐 잠시 후 다시 시도해 주세요.")
        }
    }

    /** 락을 얻기까지 기다린 시간을 `attendance.guard.lock.wait{kind=scan}`으로 기록한다. 락을 못 얻어 실패해도 기다린 시간은 남는다. */
    private fun timedLock(acquire: () -> Unit) {
        val start = System.nanoTime()
        try {
            acquire()
        } finally {
            AttendanceMetrics.recordLockWait("scan", System.nanoTime() - start)
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
    }
}
