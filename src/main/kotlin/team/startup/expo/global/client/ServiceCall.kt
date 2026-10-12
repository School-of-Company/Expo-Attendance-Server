package team.startup.expo.global.client

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics

private val logger = LoggerFactory.getLogger("team.startup.expo.global.client.ServiceCall")

/**
 * 다른 서비스를 회로 차단기로 감싸 부른다. 상대가 404로 답하면 [notFound]를 던지고(없으면 호출 실패로 본다),
 * 상대가 409로 답하고 [onConflict]가 있으면 그것을 실행한다(예외를 던져야 한다). 그 밖의 실패와 회로 차단은
 * "없음"과 구분되도록 503으로 바꾼다. 요청·응답 내용은 로그에 남기지 않는다.
 */
fun <T> CircuitBreaker.callService(
    service: String,
    notFound: ExpectedException? = null,
    onConflict: (() -> Nothing)? = null,
    block: () -> T,
): T {
    val start = System.nanoTime()
    var outcome = "success"
    try {
        return executeSupplier(block)
    } catch (e: FeignException.NotFound) {
        outcome = "not_found"
        throw notFound ?: unavailable(service, e)
    } catch (e: FeignException.Conflict) {
        outcome = "conflict"
        return onConflict?.invoke() ?: throw unavailable(service, e)
    } catch (e: CallNotPermittedException) {
        outcome = "circuit_open"
        throw unavailable(service, e)
    } catch (e: Exception) {
        outcome = "failure"
        throw unavailable(service, e)
    } finally {
        // 호출 한 번의 소요 시간과 결과만 기록한다. 요청·응답 내용은 남기지 않는다
        AttendanceMetrics.recordDownstream(service, outcome, System.nanoTime() - start)
    }
}

private fun unavailable(
    service: String,
    cause: Exception,
): ExpectedException {
    logger.warn("{} 서비스 호출 실패: {}", service, cause.javaClass.simpleName)
    return ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "$service 서비스를 잠시 사용할 수 없습니다.")
}
