package team.startup.expo.global.client

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("team.startup.expo.global.client.ServiceCall")

/**
 * 다른 서비스 호출이 실패한 원인을 로그로 남긴다. 어느 서비스의 어느 호출이 어떤 상태로, 얼마나 걸려 실패했는지를 담고,
 * 요청·응답 본문, 헤더, 전화번호, 코드, 토큰, 경로 변수와 쿼리가 들어간 URL은 남기지 않는다.
 * 예외 메시지(`message`)도 본문을 담을 수 있어 쓰지 않는다.
 *
 * 회로가 열려 있을 때는 호출마다 같은 줄이 쏟아지므로 `debug`로 낮춘다. 열림·닫힘은 [logStateTransitions]가 한 번씩 남긴다.
 */
internal fun logDownstreamFailure(
    service: String,
    cause: Exception,
    elapsedNanos: Long,
) {
    if (cause is CallNotPermittedException) {
        logger.debug("{} 서비스 호출 차단: 회로가 열려 있어 호출하지 않았다", service)
        return
    }
    val details = StringBuilder("exception=").append(cause.javaClass.simpleName)
    if (cause is FeignException && cause.status() > 0) details.append(" status=").append(cause.status())
    feignMethod(cause)?.let { details.append(" call=").append(it) }
    details.append(" elapsedMs=").append(TimeUnit.NANOSECONDS.toMillis(elapsedNanos))
    val root = rootCause(cause)
    if (root !== cause) details.append(" rootCause=").append(root.javaClass.simpleName)
    jsonPath(cause)?.let { details.append(" jsonPath=").append(it) }
    logger.warn("{} 서비스 호출 실패: {}", service, details)
}

/** Feign 클라이언트 메서드(`UserClient#recordEntry(RecordEntryReqDto)`). 경로·값은 들어 있지 않다. */
private fun feignMethod(cause: Exception): String? =
    (cause as? FeignException)
        ?.request()
        ?.requestTemplate()
        ?.methodMetadata()
        ?.configKey()

private fun rootCause(cause: Throwable): Throwable {
    var current = cause
    val seen = HashSet<Throwable>()
    while (current.cause != null && seen.add(current)) current = current.cause!!
    return current
}

/** 응답을 해석하다 실패한 JSON 필드 경로(`items[0].id`). 값과 메시지는 쓰지 않는다. */
private fun jsonPath(cause: Throwable): String? {
    var current: Throwable? = cause
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is JacksonException && current.path.isNotEmpty()) {
            return current.path
                .joinToString("") { ref ->
                    if (ref.propertyName != null) ".${ref.propertyName}" else "[${ref.index}]"
                }.removePrefix(".")
        }
        current = current.cause
    }
    return null
}

/**
 * 회로 차단기의 상태가 바뀔 때만 한 번 로그를 남긴다(닫힘→열림, 열림→반열림 등). 회로가 열려 있는 동안 호출마다 쏟아지는
 * 로그 대신 "언제 왜 열렸는지"(실패율, 느린 호출 비율)를 한 줄로 알 수 있게 한다.
 */
fun CircuitBreaker.logStateTransitions(): CircuitBreaker {
    eventPublisher.onStateTransition { event ->
        val metrics = metrics
        logger.warn(
            "{} 서비스 회로 차단기 상태 변경: {} -> {} (failureRate={}%, slowCallRate={}%)",
            event.circuitBreakerName,
            event.stateTransition.fromState,
            event.stateTransition.toState,
            metrics.failureRate,
            metrics.slowCallRate,
        )
    }
    return this
}
