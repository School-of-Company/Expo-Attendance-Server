package team.startup.expo.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 상관 ID를 정해 MDC(`requestId`)에 두고 응답 헤더로도 돌려준다. 그래서 한 요청의 모든 로그를 이어 볼 수 있고,
 * 다른 서비스 호출에도 같은 값이 헤더로 실린다([RequestIdFeignInterceptor]).
 *
 * 게이트웨이(Expo-Gateway)는 요청 ID를 만들거나 넘기는 코드가 없고 `X-User-Id`·`X-User-Role`만 정한다. 다만 프록시가
 * 클라이언트가 보낸 헤더를 그대로 넘기므로 `X-Request-Id`가 오면 그 값을 쓴다. 외부에서 온 값이라 안전한 문자만 허용하고
 * (로그 줄 위조 방지), 아니면 새로 만든다. 보안 필터 체인보다 앞에서 돌아 인증 실패 응답에도 ID가 붙는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { SAFE_ID.matches(it) } ?: generate()
        MDC.put(RequestContext.REQUEST_ID, requestId)
        response.setHeader(HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            RequestContext.KEYS.forEach { MDC.remove(it) }
        }
    }

    private fun generate(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(GENERATED_LENGTH)

    companion object {
        const val HEADER = "X-Request-Id"
        private const val GENERATED_LENGTH = 16
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
