package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 사전등록 회차 시간 조회 캐시 설정(#249).
 *
 * @property ttlSeconds 박람회 서비스에서 읽은 회차 시간을 이 시간 동안 다시 부르지 않고 쓴다. 회차 시간을 고친 직후 최대 이 시간만큼
 * 이전 시간으로 입장이 판정될 수 있다(박람회 기간 캐시와 같은 정책). 0이면 끈다.
 */
@ConfigurationProperties(prefix = "preregister-session-cache")
data class PreregisterSessionCacheProperties(
    val ttlSeconds: Long = 30,
)
