package team.startup.expo.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * `/internal` 하위 경로는 gateway를 거치지 않는 서비스 간 호출이다. `X-Internal-Token`이 설정된 시크릿과 같을 때만
 * 서비스 권한([SERVICE_AUTHORITY])을 부여하고, 아니면 인증하지 않아 401이 된다.
 *
 * gateway 라우팅 표에 `/internal` prefix를 추가하면 안 된다. 라우팅에 없는 경로는 gateway가 404로
 * 막아 주므로, 이 경로가 외부에 닿지 않는 것은 그 덕분이다.
 */
class InternalTokenAuthenticationFilter(
    properties: InternalProperties,
) : OncePerRequestFilter() {
    private val expectedDigest = sha256(properties.token)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !INTERNAL_PATH_MATCHER.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.getHeader(TOKEN_HEADER)
        // 해시한 값끼리 상수 시간으로 비교해 길이나 일치한 앞부분이 응답 시간으로 드러나지 않게 한다
        if (token != null && MessageDigest.isEqual(sha256(token), expectedDigest)) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken("internal-service", null, listOf(SimpleGrantedAuthority(SERVICE_AUTHORITY)))
        }
        filterChain.doFilter(request, response)
    }

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    companion object {
        /** 보안 규칙과 두 필터가 같은 판정을 쓴다. context path와 `/internal` 자체도 같은 기준으로 다룬다. */
        val INTERNAL_PATH_MATCHER: RequestMatcher = PathPatternRequestMatcher.withDefaults().matcher("/internal/**")
        const val TOKEN_HEADER = "X-Internal-Token"
        const val SERVICE_AUTHORITY = "ROLE_SERVICE"
    }
}
