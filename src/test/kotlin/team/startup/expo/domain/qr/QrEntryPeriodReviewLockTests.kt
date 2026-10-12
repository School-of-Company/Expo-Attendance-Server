package team.startup.expo.domain.qr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.bean.override.mockito.MockitoBean
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrEntry
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.impl.QrEntryPeriodReviewService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.lock.AdvisoryJobLock
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 종이 QR 재확인을 실제 PostgreSQL 락으로 겹치지 않게 한다(#105). */
class QrEntryPeriodReviewLockTests : IntegrationTestSupport() {
    @MockitoBean
    lateinit var expoClient: ExpoClient

    @Autowired
    lateinit var reviewService: QrEntryPeriodReviewService

    @Autowired
    lateinit var jobLock: AdvisoryJobLock

    @Autowired
    lateinit var qrTokenRepository: QrTokenRepository

    @Autowired
    lateinit var qrEntryRepository: QrEntryRepository

    @Autowired
    lateinit var clock: Clock

    @Autowired
    @Qualifier("expoCircuitBreaker")
    lateinit var expoCircuitBreaker: io.github.resilience4j.circuitbreaker.CircuitBreaker

    private val expoId = "expo-lock"
    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        expoCircuitBreaker.reset()
        qrEntryRepository.deleteAll()
        qrTokenRepository.deleteAll()
        val tokens = (1..30).map { QrToken(token = "lock-$it", expoId = expoId, category = QrCategory.GENERAL) }
        qrTokenRepository.saveAll(tokens)
        qrEntryRepository.saveAll(
            tokens.map {
                QrEntry(
                    token = it.token,
                    attendanceDate = today,
                    enteredAt = LocalDateTime.now(clock),
                    periodCheck = QrEntryPeriodCheck.PENDING,
                )
            },
        )
    }

    private fun pendingCount() = qrEntryRepository.findAll().count { it.periodCheck == QrEntryPeriodCheck.PENDING }

    private fun givenExpoPeriod(delayMillis: Long = 0) {
        doAnswer {
            Thread.sleep(delayMillis)
            ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())
        }.`when`(expoClient).getPeriod(anyString())
    }

    @Test
    fun `다른 연결이 그 박람회의 락을 쥐고 있으면 재확인을 건너뛰고 놓으면 처리한다`() {
        givenExpoPeriod()
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        // 다른 인스턴스가 같은 락을 쥐고 있는 상황이다(같은 키를 다른 연결에서 잡는다)
        val holder =
            executor.submit {
                jobLock.runIfAcquired("qr-entry-review:$expoId") {
                    holding.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    1
                }
            }
        try {
            holding.await(10, TimeUnit.SECONDS) shouldBe true

            reviewService.reviewPending() shouldBe 0
            pendingCount() shouldBe 30
            verify(expoClient, never()).getPeriod(anyString())
        } finally {
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            executor.shutdownNow()
        }

        // 락이 풀리면 다음 주기에 처리된다
        reviewService.reviewPending() shouldBe 30
        pendingCount() shouldBe 0
    }

    @Test
    fun `두 번의 재확인이 동시에 돌아도 같은 입장을 한 번만 처리한다`() {
        // 박람회 서비스 응답을 느리게 해 두 실행이 겹치게 한다
        givenExpoPeriod(delayMillis = 700)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val results =
                (1..2).map {
                    executor.submit<Int> {
                        start.await()
                        reviewService.reviewPending()
                    }
                }
            start.countDown()
            val changed = results.map { it.get(30, TimeUnit.SECONDS) }

            // 한쪽이 30건을 처리하고 다른 쪽은 건너뛴다. 둘 다 처리했다면 합이 30을 넘거나 박람회 서비스를 두 번 불렀을 것이다
            changed.sum() shouldBe 30
            changed.sorted() shouldBe listOf(0, 30)
            verify(expoClient, times(1)).getPeriod(expoId)
            pendingCount() shouldBe 0
        } finally {
            executor.shutdownNow()
        }
        verify(expoClient, atLeastOnce()).getPeriod(expoId)
    }

    @Test
    fun `락을 쥔 실행이 예외로 끝나면 락이 풀려 다음 실행이 처리한다`() {
        doAnswer { throw IllegalStateException("boom") }.`when`(expoClient).getPeriod(anyString())
        runCatching { reviewService.reviewPending() }

        expoCircuitBreaker.reset()
        givenExpoPeriod()
        reviewService.reviewPending() shouldBe 30
    }
}
