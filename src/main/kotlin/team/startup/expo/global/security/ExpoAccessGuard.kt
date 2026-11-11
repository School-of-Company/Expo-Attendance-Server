package team.startup.expo.global.security

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.exception.ExpectedException

/** Expo가 토큰의 박람회·역할·회수 여부를 매 요청 판정한다. 성공 결과도 캐시하지 않는다. */
@Component
class ExpoAccessGuard(
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val request: HttpServletRequest,
) {
    fun requireAccess(
        expoId: String,
        permission: String? = null,
    ) {
        val authorization = request.getHeader("Authorization")
        if (authorization.isNullOrBlank()) throw ExpectedException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.")
        val allowed =
            expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못했습니다.")) {
                try {
                    expoClient.requireAdminAccess(expoId, authorization, permission)
                    true
                } catch (_: FeignException.Forbidden) {
                    false
                } catch (_: FeignException.Unauthorized) {
                    false
                }
            }
        if (!allowed) throw ExpectedException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")
    }
}
