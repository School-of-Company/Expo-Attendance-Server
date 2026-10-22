package team.startup.expo.support

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import team.startup.expo.domain.attendance.service.PreregisterSessionValidator
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** DB와 Spring 컨텍스트 없이 도는 단위 테스트(`BehaviorSpec`)가 함께 쓰는 도우미. */
object UnitTestSupport {
    /** 2026-05-10 03:00:00 UTC. */
    val NOW: Instant = Instant.parse("2026-05-10T03:00:00Z")

    fun fixedClock(instant: Instant = NOW): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    fun preregisterValidator(): PreregisterSessionValidator =
        mockk<PreregisterSessionValidator>(relaxed = true).also {
            every { it.activeField(any(), any(), any()) } returns null
        }

    /** `TransactionTemplate.execute`가 콜백을 그대로 실행하도록 트랜잭션 매니저를 흉내 낸다. */
    fun transactionManager(): PlatformTransactionManager =
        mockk<PlatformTransactionManager>(relaxed = true).also {
            every { it.getTransaction(any()) } returns mockk<TransactionStatus>(relaxed = true)
        }

    fun circuitBreaker(name: String = "test"): CircuitBreaker = CircuitBreaker.ofDefaults(name)

    private fun request() = Request.create(Request.HttpMethod.GET, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate())

    fun feignNotFound(): FeignException = FeignException.NotFound("not found", request(), null, emptyMap())

    fun feignConflict(): FeignException = FeignException.Conflict("conflict", request(), null, emptyMap())

    fun feignServerError(): FeignException = FeignException.InternalServerError("error", request(), null, emptyMap())
}

/** [block]이 [status]와 [message]의 [ExpectedException]을 던지는지 확인한다. */
fun shouldFailWith(
    status: HttpStatus,
    message: String,
    block: () -> Unit,
) {
    val e = shouldThrow<ExpectedException> { block() }
    e.status shouldBe status
    e.message shouldBe message
}
