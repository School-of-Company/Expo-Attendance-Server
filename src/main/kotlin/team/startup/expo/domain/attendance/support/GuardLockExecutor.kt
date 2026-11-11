package team.startup.expo.domain.attendance.support

import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics

/**
 * 취소 보호 advisory 락을 잡는 짧은 트랜잭션의 공통 뼈대다. 스캔([CancellationGuardGate], 공유 락), 취소 보호(배타 락),
 * 날짜별 현장 방문(배타 락)이 함께 쓴다.
 *
 * [execute]는 트랜잭션을 열고 `lock_timeout`을 [CancellationGuardGate.LOCK_TIMEOUT]으로 맞춘 뒤 블록을 실행한다. 어떤 락을 어떤 순서로
 * 잡을지는 블록이 정한다. 순서 규칙(#167)은 박람회 락 → 참가자 락 → 등록 ID 락 → 연수자 락이고, 반대로 잡으면 교착이 난다.
 * 락은 모두 트랜잭션 단위라 블록이 끝나면(커밋이든 롤백이든) 풀린다. 락을 제때 얻지 못하면 호출한 쪽이 넘긴 문구로 503을 던진다.
 *
 * 빈이 아니다. 각 사용처가 자기 트랜잭션 매니저와 리포지토리로 만든다.
 */
class GuardLockExecutor(
    transactionManager: PlatformTransactionManager,
    private val cancellationGuardRepository: CancellationGuardRepository,
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun <T> execute(
        unavailableMessage: String,
        block: () -> T,
    ): T {
        try {
            return requireNotNull(
                transaction.execute {
                    cancellationGuardRepository.setLockTimeout(CancellationGuardGate.LOCK_TIMEOUT)
                    block()
                },
            )
        } catch (_: CannotAcquireLockException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, unavailableMessage)
        }
    }

    /** 공유 락을 잡고 기다린 시간을 `attendance.guard.lock.wait{kind=[waitKind]}`로 기록한다. [execute] 안에서만 부른다. */
    fun lockShared(
        key: String,
        waitKind: String,
    ) = timed(waitKind) { cancellationGuardRepository.lockShared(key) }

    /** 배타 락을 잡고 기다린 시간을 `attendance.guard.lock.wait{kind=[waitKind]}`로 기록한다. [execute] 안에서만 부른다. */
    fun lockExclusive(
        key: String,
        waitKind: String,
    ) = timed(waitKind) { cancellationGuardRepository.lockExclusive(key) }

    /** 배타 락을 잡는다. 기다린 시간은 기록하지 않는다. [execute] 안에서만 부른다. */
    fun lockExclusive(key: String) {
        cancellationGuardRepository.lockExclusive(key)
    }

    /** 락을 못 얻어 실패해도 기다린 시간은 남는다. */
    private fun timed(
        waitKind: String,
        acquire: () -> Unit,
    ) {
        val start = System.nanoTime()
        try {
            acquire()
        } finally {
            AttendanceMetrics.recordLockWait(waitKind, System.nanoTime() - start)
        }
    }

    companion object {
        /** 스캔이 잡는 공유 락의 대기 지표 태그. */
        const val WAIT_SCAN = "scan"

        /** 취소 보호가 잡는 배타 락의 대기 지표 태그. */
        const val WAIT_CANCEL = "cancel"
    }
}
