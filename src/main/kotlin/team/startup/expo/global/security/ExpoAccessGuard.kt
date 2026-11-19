package team.startup.expo.global.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.exception.ExpectedException
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * Expo가 토큰의 박람회·역할·회수 여부를 판정한다. 허용 판정만 `expo-access-cache.ttl-seconds` 동안 기억해, 그보다 짧은 Expo 장애에
 * 입구 스캔이 멈추지 않게 한다. 거절과 실패는 기억하지 않는다. 키에는 토큰 원문 대신 SHA-256을 쓴다.
 */
@Component
class ExpoAccessGuard(
    private val expoClient: ExpoClient,
    @Qualifier("expoAccessCircuitBreaker") private val circuitBreaker: CircuitBreaker,
    private val request: HttpServletRequest,
    @Value("\${expo-access-cache.ttl-seconds:30}") private val ttlSeconds: Long,
) {
    private val allowed: Cache<String, Boolean> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(maxOf(1, ttlSeconds)))
            .maximumSize(MAX_CACHED)
            .build()

    fun requireAccess(
        expoId: String,
        permission: String? = null,
    ) {
        val authorization = request.getHeader("Authorization")
        if (authorization.isNullOrBlank()) throw ExpectedException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.")
        val key = "${sha256(authorization)}|$expoId|${permission.orEmpty()}"
        if (ttlSeconds > 0 && allowed.getIfPresent(key) != null) return
        if (!ask(expoId, authorization, permission)) throw ExpectedException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")
        if (ttlSeconds > 0) allowed.put(key, true)
    }

    private fun ask(
        expoId: String,
        authorization: String,
        permission: String?,
    ): Boolean =
        circuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못했습니다.")) {
            try {
                expoClient.requireAdminAccess(expoId, authorization, permission)
                true
            } catch (_: FeignException.Forbidden) {
                false
            } catch (_: FeignException.Unauthorized) {
                false
            }
        }

    private fun sha256(value: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private companion object {
        const val MAX_CACHED = 10_000L
    }
}
