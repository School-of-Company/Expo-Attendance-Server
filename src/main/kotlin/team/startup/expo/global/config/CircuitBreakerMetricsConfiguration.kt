package team.startup.expo.global.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 다른 서비스 호출의 회로 차단기 상태를 메트릭으로 내보낸다(#101). 회로 차단기를 `CircuitBreaker.of`로 직접 만들어 Spring Boot의
 * Resilience4j 자동 바인딩이 적용되지 않으므로 여기서 게이지를 등록한다. 대상 서비스는 `name` 태그로 구분한다.
 *
 * - `attendance.circuit.breaker.state`: 0 닫힘, 1 열림, 2 반열림(그 밖의 상태는 3)
 * - `attendance.circuit.breaker.failure.rate`, `attendance.circuit.breaker.slow.call.rate`: 최근 창의 비율(%), 호출이 모자라면 -1
 */
@Configuration
class CircuitBreakerMetricsConfiguration {
    @Bean
    fun circuitBreakerMetrics(
        @Qualifier("userCircuitBreaker") user: CircuitBreaker,
        @Qualifier("expoCircuitBreaker") expo: CircuitBreaker,
        @Qualifier("applicationCircuitBreaker") application: CircuitBreaker,
    ): MeterBinder =
        MeterBinder { registry ->
            listOf(user, expo, application).forEach { breaker ->
                Gauge
                    .builder("attendance.circuit.breaker.state", breaker) { stateValue(it.state) }
                    .tag("name", breaker.name)
                    .description("회로 차단기 상태(0 닫힘, 1 열림, 2 반열림)")
                    .register(registry)
                Gauge
                    .builder("attendance.circuit.breaker.failure.rate", breaker) { it.metrics.failureRate.toDouble() }
                    .tag("name", breaker.name)
                    .register(registry)
                Gauge
                    .builder("attendance.circuit.breaker.slow.call.rate", breaker) { it.metrics.slowCallRate.toDouble() }
                    .tag("name", breaker.name)
                    .register(registry)
            }
        }

    private fun stateValue(state: CircuitBreaker.State): Double =
        when (state) {
            CircuitBreaker.State.CLOSED -> 0.0
            CircuitBreaker.State.OPEN -> 1.0
            CircuitBreaker.State.HALF_OPEN -> 2.0
            else -> 3.0
        }
}
