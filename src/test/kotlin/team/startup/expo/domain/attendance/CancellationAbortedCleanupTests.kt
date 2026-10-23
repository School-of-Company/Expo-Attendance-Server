package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.support.CancellationAbortedCleanupScheduler
import team.startup.expo.domain.attendance.support.CancellationAbortedCleanupService
import java.time.LocalDateTime

/** 보관 기간이 지난 중단된 취소 기록만 지우고 나머지는 남긴다(#105). */
@TestPropertySource(properties = ["cancellation-aborted-retention.days=30"])
class CancellationAbortedCleanupTests : team.startup.expo.support.IntegrationTestSupport() {
    @Autowired
    lateinit var cleanupService: CancellationAbortedCleanupService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }

    @Autowired
    lateinit var guardRepository: CancellationGuardRepository

    @Autowired
    lateinit var context: org.springframework.context.ApplicationContext

    @Test
    fun `보관일이 지정되면 정리 작업이 등록된다`() {
        context.getBeansOfType(CancellationAbortedCleanupScheduler::class.java).size shouldBe 1
    }

    @Test
    fun `보관일보다 오래된 중단 기록만 지우고 최근 기록은 남긴다`() {
        val expoId = "expo-cleanup"
        val now = LocalDateTime.now()
        tx.execute { guardRepository.insertAborted(expoId, 1L, "old-op", now.minusDays(31)) }
        tx.execute { guardRepository.insertAborted(expoId, 2L, "recent-op", now.minusDays(29)) }

        val deleted = cleanupService.cleanup()

        (deleted >= 1) shouldBe true
        guardRepository.existsAborted(expoId, 1L, "old-op") shouldBe false
        guardRepository.existsAborted(expoId, 2L, "recent-op") shouldBe true
    }
}
