package team.startup.expo.internal

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import team.startup.expo.global.config.CircuitBreakerMetricsConfiguration

/** DB 없이 도는 단위 테스트: 서비스별 회로 차단기 상태가 `name` 태그로 게이지에 나오는지 확인한다. */
class CircuitBreakerMetricsTests {
    @Test
    fun `서비스별 회로 차단기 상태를 name 태그 게이지로 등록하고 상태 변화를 따라간다`() {
        val user = CircuitBreaker.ofDefaults("user")
        val expo = CircuitBreaker.ofDefaults("expo")
        val application = CircuitBreaker.ofDefaults("application")
        val registry = SimpleMeterRegistry()

        CircuitBreakerMetricsConfiguration().circuitBreakerMetrics(user, expo, application).bindTo(registry)

        fun state(name: String) =
            registry
                .get("attendance.circuit.breaker.state")
                .tag("name", name)
                .gauge()
                .value()
        state("user") shouldBe 0.0
        state("expo") shouldBe 0.0
        state("application") shouldBe 0.0

        expo.transitionToOpenState()
        state("expo") shouldBe 1.0
        state("user") shouldBe 0.0

        expo.transitionToHalfOpenState()
        state("expo") shouldBe 2.0

        registry
            .get("attendance.circuit.breaker.failure.rate")
            .tag("name", "user")
            .gauge()
            .value() shouldBe -1.0
    }
}
