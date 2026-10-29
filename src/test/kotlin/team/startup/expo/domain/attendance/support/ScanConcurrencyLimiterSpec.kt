package team.startup.expo.domain.attendance.support

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import team.startup.expo.global.config.ScanConcurrencyProperties
import team.startup.expo.global.exception.ExpectedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScanConcurrencyLimiterSpec : BehaviorSpec() {
    private fun limiter(
        poolSize: Int,
        reserve: Int = 6,
        waitMs: Long = 100,
    ) = ScanConcurrencyLimiter(ScanConcurrencyProperties(reserve, waitMs), poolSize)

    /** 허용 수만큼 스레드가 자리를 쥔 채 [release]가 풀릴 때까지 기다리게 하고, 모두 자리를 쥔 뒤에 돌아온다. */
    private fun occupy(
        limiter: ScanConcurrencyLimiter,
        release: CountDownLatch,
    ): () -> Unit {
        val pool = Executors.newFixedThreadPool(limiter.permits)
        val entered = CountDownLatch(limiter.permits)
        repeat(limiter.permits) {
            pool.submit {
                limiter.limited {
                    entered.countDown()
                    release.await()
                }
            }
        }
        entered.await(5, TimeUnit.SECONDS) shouldBe true
        return { pool.shutdown() }
    }

    init {
        given("풀 크기에서 reserve를 뺀 허용 수") {
            then("풀 30, reserve 6이면 24다") {
                ScanConcurrencyLimiter.permitsFor(30, 6) shouldBe 24
                limiter(30).permits shouldBe 24
            }
            then("풀이 reserve 이하여도 최소 1이다") {
                ScanConcurrencyLimiter.permitsFor(6, 6) shouldBe 1
                ScanConcurrencyLimiter.permitsFor(3, 6) shouldBe 1
                limiter(poolSize = 4).permits shouldBe 1
            }
        }

        given("허용 수 이내의 동시 스캔") {
            val limiter = limiter(poolSize = 10, reserve = 6)
            `when`("허용 수만큼 동시에 들어오면") {
                val release = CountDownLatch(1)
                val shutdown = occupy(limiter, release)
                release.countDown()
                shutdown()
                then("모두 통과하고 결과를 돌려준다") {
                    limiter.limited { "ok" } shouldBe "ok"
                }
            }
        }

        given("허용 수가 모두 찬 상태") {
            val limiter = limiter(poolSize = 8, reserve = 6, waitMs = 100)
            val release = CountDownLatch(1)
            val shutdown = occupy(limiter, release)
            `when`("wait-ms 안에 자리를 못 얻으면") {
                var ran = false
                val start = System.nanoTime()
                val e = shouldThrow<ExpectedException> { limiter.limited { ran = true } }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                then("블록을 실행하지 않고 503으로 거절한다") {
                    e.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    ran shouldBe false
                    elapsedMs shouldBeLessThan 1000
                }
            }
            `when`("자리가 반환되면") {
                release.countDown()
                shutdown()
                then("다시 받는다") {
                    limiter.limited { "ok" } shouldBe "ok"
                }
            }
        }

        given("블록이 예외를 던지는 스캔") {
            val limiter = limiter(poolSize = 7, reserve = 6, waitMs = 50)
            `when`("허용 수(1)보다 많이 연달아 실패해도") {
                repeat(5) {
                    shouldThrow<IllegalStateException> { limiter.limited { error("boom") } }
                }
                then("자리가 반환돼 다음 스캔이 통과한다") {
                    limiter.limited { "ok" } shouldBe "ok"
                }
            }
        }
    }
}
