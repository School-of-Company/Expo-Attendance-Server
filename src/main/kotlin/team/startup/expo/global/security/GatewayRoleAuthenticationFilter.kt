package team.startup.expo.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 게이트웨이가 JWT를 검증한 뒤 넘기는 `X-User-Id`, `X-User-Role`로 사용자 권한을 부여한다. 이 서비스는 토큰을
 * 직접 파싱하지 않는다. 게이트웨이는 요청이 보낸 두 헤더를 먼저 지우고 검증한 토큰의 값만 채우므로, 게이트웨이를
 * 거친 요청에서는 위조할 수 없다. 반대로 서비스에 직접 닿을 수 있는 경로에서는 헤더를 위조할 수 있으므로 이 서비스는
 * 게이트웨이 라우팅으로만 외부에 노출되어야 한다(서비스 간 호출은 `/internal`과 `X-Internal-Token`을 쓴다).
 *
 * 사용자 ID가 있으면 인증하며 역할 없는 토큰에는 권한을 부여하지 않는다. 잘못된 역할 형식은 인증하지 않는다.
 */
class GatewayRoleAuthenticationFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        InternalTokenAuthenticationFilter.INTERNAL_PATH_MATCHER.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = request.getHeader(USER_ID_HEADER)
        val role = request.getHeader(USER_ROLE_HEADER)
        if (!userId.isNullOrBlank() && (role == null || ROLE_PATTERN.matches(role))) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, role?.let { listOf(SimpleGrantedAuthority(it)) } ?: emptyList())
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
        const val USER_ROLE_HEADER = "X-User-Role"
        const val ADMIN_AUTHORITY = "ROLE_ADMIN"
        const val STAFF_AUTHORITY = "ROLE_STAFF"
        private val ROLE_PATTERN = Regex("^[A-Za-z0-9_.:-]{1,64}$")
    }
}
