package team.startup.expo.global.client

import feign.FeignException
import feign.RetryableException
import feign.codec.DecodeException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * 다른 서비스를 회로 차단기로 감싸 부른다. 상대가 404로 답하면 [notFound]를 던지고(없으면 호출 실패로 본다),
 * 상대가 409로 답하고 [onConflict]가 있으면 그것을 실행한다(예외를 던져야 한다). 그 밖의 실패와 회로 차단은
 * "없음"과 구분되도록 503으로 바꾼다. 요청·응답 내용은 로그에 남기지 않는다.
 *
 * [onOutcomeUnknown]이 있으면, 요청이 상대에게 닿았을 수 있는데 응답을 받지 못한 실패([isOutcomeUnknown])일 때 503 대신 그것을
 * 실행한다(예외를 던져야 한다). 쓰기 호출이 실제로 반영됐는지 호출자가 다시 확인할 때 쓴다(#287).
 */
fun <T> CircuitBreaker.callService(
    service: String,
    notFound: ExpectedException? = null,
    onConflict: (() -> Nothing)? = null,
    onOutcomeUnknown: (() -> Nothing)? = null,
    block: () -> T,
): T {
    val start = System.nanoTime()
    var outcome = "success"
    try {
        return executeSupplier(block)
    } catch (e: FeignException.NotFound) {
        outcome = "not_found"
        throw notFound ?: unavailable(service, e, start)
    } catch (e: FeignException.Conflict) {
        outcome = "conflict"
        return onConflict?.invoke() ?: throw unavailable(service, e, start)
    } catch (e: CallNotPermittedException) {
        outcome = "circuit_open"
        throw unavailable(service, e, start)
    } catch (e: Exception) {
        outcome = "failure"
        val failure = unavailable(service, e, start)
        if (onOutcomeUnknown != null && isOutcomeUnknown(e)) onOutcomeUnknown()
        throw failure
    } finally {
        // 호출 한 번의 소요 시간과 결과만 기록한다. 요청·응답 내용은 남기지 않는다
        AttendanceMetrics.recordDownstream(service, outcome, System.nanoTime() - start)
    }
}

private fun unavailable(
    service: String,
    cause: Exception,
    startNanos: Long,
): ExpectedException {
    // 원인(서비스, 예외, 상태, 소요 시간)은 DownstreamLog.kt가 남긴다. 본문과 개인정보는 남기지 않는다
    logDownstreamFailure(service, cause, System.nanoTime() - startNanos)
    return ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "$service 서비스를 잠시 사용할 수 없습니다.")
}

/**
 * 요청을 보냈을 수 있지만 결과를 모르는 실패인지 판단한다. 읽기 타임아웃·연결 끊김처럼 상대가 응답하기 전에 끊긴 입출력 오류는
 * Feign이 상태 없는 [RetryableException]으로 던지고, 응답을 받았지만 해석하지 못하면 [DecodeException]이다(상대는 처리를 끝냈다).
 * 연결 거부·경로 없음·주소 해석 실패는 요청이 나가지 않았으므로 결과를 안다. 상대가 상태 코드로 답한 실패(5xx, `Retry-After`가 붙은
 * [RetryableException])와 회로 차단(호출하지 않음)도 여기에 들지 않는다.
 */
internal fun isOutcomeUnknown(cause: Exception): Boolean {
    if (cause is DecodeException) return true
    if (cause !is RetryableException || cause.status() > 0) return false
    return generateSequence<Throwable>(cause) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .none { it is ConnectException || it is NoRouteToHostException || it is UnknownHostException }
}

private const val MAX_CAUSE_DEPTH = 10
