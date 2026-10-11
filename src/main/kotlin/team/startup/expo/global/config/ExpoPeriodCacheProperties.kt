package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 박람회 기간 조회 캐시 설정.
 *
 * @property ttlSeconds 박람회 서비스에서 읽은 기간을 이 시간 동안 다시 부르지 않고 쓴다. 박람회 기간을 고친 직후 최대 이 시간만큼
 * 이전 기간으로 판단될 수 있다. 0이면 끈다.
 */
@ConfigurationProperties(prefix = "expo-period-cache")
data class ExpoPeriodCacheProperties(
    val ttlSeconds: Long = 30,
)
