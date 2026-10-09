package team.startup.expo.internal

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import team.startup.expo.global.client.ClientCircuitBreakerConfiguration
import java.util.concurrent.TimeUnit

class ClientCircuitBreakerTests {
    private val configuration = ClientCircuitBreakerConfiguration()

    private fun request() = Request.create(Request.HttpMethod.GET, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate())

    private fun notFound() = FeignException.NotFound("not found", request(), null, emptyMap())

    private fun conflict() = FeignException.Conflict("conflict", request(), null, emptyMap())

    private fun serverError() = FeignException.InternalServerError("error", request(), null, emptyMap())

    private fun CircuitBreaker.failWith(
        exception: FeignException,
        calls: Int,
    ) {
        repeat(calls) {
            assertThrows<FeignException> { executeSupplier { throw exception } }
        }
    }

    @Test
    fun `404와 409는 아무리 반복해도 회로를 열지 않고 그대로 던져진다`() {
        val notFoundBreaker = configuration.userCircuitBreaker()
        val conflictBreaker = configuration.expoCircuitBreaker()

        notFoundBreaker.failWith(notFound(), IGNORED_CALLS)
        conflictBreaker.failWith(conflict(), IGNORED_CALLS)

        notFoundBreaker.state shouldBe CircuitBreaker.State.CLOSED
        conflictBreaker.state shouldBe CircuitBreaker.State.CLOSED
    }

    @Test
    fun `서버 오류가 반복되면 회로가 열린다`() {
        val breaker = configuration.userCircuitBreaker()

        breaker.failWith(serverError(), MIN_CALLS_TO_OPEN)

        breaker.state shouldBe CircuitBreaker.State.OPEN
    }

    @Test
    fun `성공해도 대부분 2초보다 느리면 회로가 열린다`() {
        val breaker = configuration.userCircuitBreaker()

        repeat(MIN_CALLS_TO_OPEN) { breaker.onSuccess(SLOW_MILLIS, TimeUnit.MILLISECONDS) }

        breaker.state shouldBe CircuitBreaker.State.OPEN
    }

    @Test
    fun `빠른 호출이 대부분이면 가끔 느려도 회로가 열리지 않는다`() {
        val breaker = configuration.userCircuitBreaker()

        repeat(MIN_CALLS_TO_OPEN * 2) { index ->
            breaker.onSuccess(if (index % 5 == 0) SLOW_MILLIS else FAST_MILLIS, TimeUnit.MILLISECONDS)
        }

        breaker.state shouldBe CircuitBreaker.State.CLOSED
    }

    private companion object {
        const val SLOW_MILLIS = 2_500L
        const val FAST_MILLIS = 100L

        // 설정의 슬라이딩 윈도우(20)를 채우고도 남는 횟수
        const val IGNORED_CALLS = 30

        // 설정의 최소 호출 수(10). 이 횟수만큼 실패하면 회로가 열린다
        const val MIN_CALLS_TO_OPEN = 10
    }
}
