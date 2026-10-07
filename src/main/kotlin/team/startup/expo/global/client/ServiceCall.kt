package team.startup.expo.global.client

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException

private val logger = LoggerFactory.getLogger("team.startup.expo.global.client.ServiceCall")

/**
 * 다른 서비스를 회로 차단기로 감싸 부른다. 상대가 404로 답하면 [notFound]를 던지고(없으면 호출 실패로 본다),
 * 그 밖의 실패와 회로 차단은 "없음"과 구분되도록 503으로 바꾼다. 요청·응답 내용은 로그에 남기지 않는다.
 */
fun <T> CircuitBreaker.callService(
    service: String,
    notFound: ExpectedException? = null,
    block: () -> T,
): T =
    try {
        executeSupplier(block)
    } catch (e: FeignException.NotFound) {
        throw notFound ?: unavailable(service, e)
    } catch (e: Exception) {
        throw unavailable(service, e)
    }

private fun unavailable(
    service: String,
    cause: Exception,
): ExpectedException {
    logger.warn("{} 서비스 호출 실패: {}", service, cause.javaClass.simpleName)
    return ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "$service 서비스를 잠시 사용할 수 없습니다.")
}
