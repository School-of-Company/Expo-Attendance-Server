package team.startup.expo.global.lock

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 인스턴스가 여러 개일 때 같은 주기 작업이 겹쳐 돌지 않게 하는 PostgreSQL 어드바이저리 락이다(별도 의존성이 없다).
 * 트랜잭션 수준 락(`pg_try_advisory_xact_lock`)이라 작업이 끝나거나 인스턴스가 죽어 연결이 끊기면 저절로 풀리므로
 * 풀리지 않은 락이 남지 않는다. 락을 못 잡으면 기다리지 않고 건너뛴다(다른 인스턴스가 이미 돌고 있으므로).
 *
 * [block]은 락을 잡은 트랜잭션 안에서 실행된다. 안에서 부르는 `@Transactional` 저장소 메서드는 같은 트랜잭션에 합류하고,
 * [block]이 예외를 던지면 그 트랜잭션은 롤백된다. 락을 오래 쥐지 않도록 [runIfAcquired]의 key는 작업 전체가 아니라
 * 처리 단위(예: 박람회)마다 둔다.
 */
@Component
class AdvisoryJobLock(
    transactionManager: PlatformTransactionManager,
    private val entityManager: EntityManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /** 락을 잡으면 [block]의 결과를, 다른 곳이 쥐고 있어 건너뛰면 `null`을 돌려준다. */
    fun <T : Any> runIfAcquired(
        key: String,
        block: () -> T,
    ): T? =
        transaction.execute {
            if (tryLock(key)) block() else null
        }

    private fun tryLock(key: String): Boolean =
        entityManager
            .createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtextextended(:key, 0))")
            .setParameter("key", key)
            .singleResult as Boolean
}
