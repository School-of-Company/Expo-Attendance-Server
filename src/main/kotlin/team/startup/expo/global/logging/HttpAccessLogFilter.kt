package team.startup.expo.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.io.IOException

/**
 * 요청마다 접근 로그를 한 줄 남긴다: `<METHOD> <경로> status: <상태> (<ms>ms)` (예: `PATCH /attendance/{expoId} status: 200 (12ms)`).
 * 모든 서비스가 같은 형식·`module`(`HttpAccess`)로 남겨 Grafana `Expo / Logs`에서 요청 흐름과 상태 코드를 본다.
 *
 * - 경로는 매칭된 경로 템플릿을 쓰고, 없으면(보안 필터 거절, 404) 쿼리를 뺀 요청 URI를 200자로 자른다.
 *   쿼리·헤더·쿠키·본문은 남기지 않는다(전화번호·토큰).
 * - 레벨: 4xx는 WARN, 5xx는 ERROR, 나머지는 INFO. 클라이언트가 끊으면(응답 쓰기 중 `IOException`) 499를 ERROR로 한 번만 남긴다.
 * - `/actuator` 하위와 API 문서 경로는 남기지 않는다.
 * - [RequestIdFilter] 안쪽, 보안 필터 체인보다 바깥에서 돌아 401/403 거절도 남고 MDC `requestId`가 붙는다.
 * - `logging.http-access.enabled`(env `LOGGING_HTTP_ACCESS_ENABLED`, 기본 `true`)로 끈다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class HttpAccessLogFilter(
    @Value("\${logging.http-access.enabled:true}") private val enabled: Boolean,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!enabled) return true
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return SKIPPED_PREFIXES.any { path == it || path.startsWith("$it/") || path.startsWith("$it.") }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        var status: Int? = null
        try {
            filterChain.doFilter(request, response)
        } catch (e: IOException) {
            status = CLIENT_CLOSED_REQUEST
            throw e
        } catch (e: Exception) {
            status = if (isClientAbort(e)) CLIENT_CLOSED_REQUEST else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            throw e
        } finally {
            log(request, status ?: response.status, (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
        }
    }

    /** 감싼 예외 안에 Tomcat `ClientAbortException`이 있으면 클라이언트가 끊은 것이다 */
    private fun isClientAbort(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH).any {
            it.javaClass.simpleName ==
                "ClientAbortException"
        }

    private fun log(
        request: HttpServletRequest,
        status: Int,
        elapsedMs: Long,
    ) {
        val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        val path = route ?: request.requestURI.take(MAX_PATH_LENGTH)
        val format = "{} {} status: {} ({}ms)"
        when {
            status >= SERVER_ERROR || status == CLIENT_CLOSED_REQUEST -> accessLogger.error(format, request.method, path, status, elapsedMs)
            status >= CLIENT_ERROR -> accessLogger.warn(format, request.method, path, status, elapsedMs)
            else -> accessLogger.info(format, request.method, path, status, elapsedMs)
        }
    }

    companion object {
        /** 이름에 점이 없어 [ModuleNameConverter]가 그대로 `module`로 쓴다 */
        const val LOGGER_NAME = "HttpAccess"
        private val accessLogger = LoggerFactory.getLogger(LOGGER_NAME)

        private const val CLIENT_CLOSED_REQUEST = 499
        private const val CLIENT_ERROR = 400
        private const val SERVER_ERROR = 500
        private const val MAX_PATH_LENGTH = 200
        private const val NANOS_PER_MILLI = 1_000_000
        private const val MAX_CAUSE_DEPTH = 10

        private val SKIPPED_PREFIXES =
            listOf(
                "/actuator",
                "/attendance/swagger-ui",
                "/attendance/v3/api-docs",
                "/swagger-ui",
                "/v3/api-docs",
            )
    }
}
