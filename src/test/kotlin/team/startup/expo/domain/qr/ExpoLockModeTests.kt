package team.startup.expo.domain.qr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.support.IntegrationTestSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 박람회 락은 삭제만 배타이고 다른 쓰기는 공유라서, 쓰기끼리는 서로 기다리지 않고 삭제와만 겹치지 않는다(#248). 실제 PostgreSQL로 확인한다. */
class ExpoLockModeTests : IntegrationTestSupport() {
    @Autowired
    lateinit var deletedExpoRepository: DeletedExpoRepository

    @Autowired
    lateinit var cancellationGuardRepository: CancellationGuardRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private val expoId = "expo-lock-mode"

    private fun <T> inTransaction(block: () -> T): T? =
        TransactionTemplate(transactionManager).execute {
            cancellationGuardRepository.setLockTimeout("500ms")
            block()
        }

    /** 다른 연결이 공유 락을 쥔 동안 [block]을 실행한다. */
    private fun whileSharedHeld(block: () -> Unit) {
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val holder =
            executor.submit {
                inTransaction {
                    deletedExpoRepository.lockExpoShared(expoId)
                    holding.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        try {
            holding.await(10, TimeUnit.SECONDS) shouldBe true
            block()
        } finally {
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }

    @Test
    fun `다른 쓰기가 공유 락을 쥐고 있어도 공유 락은 바로 잡힌다`() {
        whileSharedHeld {
            inTransaction { deletedExpoRepository.lockExpoShared(expoId) } shouldBe 1
        }
    }

    @Test
    fun `공유 락을 쥔 쓰기가 끝나기 전에는 삭제의 배타 락을 잡지 못하고 끝나면 잡는다`() {
        whileSharedHeld {
            shouldThrow<PessimisticLockingFailureException> { inTransaction { deletedExpoRepository.lockExpo(expoId) } }
        }

        inTransaction { deletedExpoRepository.lockExpo(expoId) } shouldBe 1
    }
}
