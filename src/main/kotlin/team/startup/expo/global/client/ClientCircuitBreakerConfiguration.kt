package team.startup.expo.global.client

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * 다른 서비스를 부르는 Feign 호출마다 회로 차단기를 하나씩 둔다. 장애 난 서비스 하나가 다른 서비스 호출까지
 * 막지 않도록 서비스별로 나눈다.
 *
 * 404(없음)와 409(이미 있음)는 상대가 정상적으로 답한 것이라 실패로 세지 않는다. 존재하지 않는 id를 반복해서
 * 조회하거나 같은 입장을 다시 기록해도 회로가 열리지 않게 하기 위해서다. 이 예외는 무시 대상일 뿐 그대로
 * 호출자에게 던져지므로, 호출자가 "없음"과 "호출 실패(503)"를 직접 구분한다.
 */
@Configuration
class ClientCircuitBreakerConfiguration {
    @Bean
    fun userCircuitBreaker(): CircuitBreaker = circuitBreaker("user")

    @Bean
    fun expoCircuitBreaker(): CircuitBreaker = circuitBreaker("expo")

    /** 최근 20번 중 10번 이상 호출되고 절반 이상 실패하면 10초 동안 열려 상대를 부르지 않고 바로 실패한다. */
    private fun circuitBreaker(name: String): CircuitBreaker =
        CircuitBreaker.of(
            name,
            CircuitBreakerConfig
                .custom()
                .slidingWindowSize(WINDOW_SIZE)
                .minimumNumberOfCalls(MIN_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(OPEN_SECONDS))
                .ignoreExceptions(FeignException.NotFound::class.java, FeignException.Conflict::class.java)
                .build(),
        )

    private companion object {
        const val WINDOW_SIZE = 20
        const val MIN_CALLS = 10
        const val FAILURE_RATE_THRESHOLD = 50f
        const val OPEN_SECONDS = 10L
    }
}
