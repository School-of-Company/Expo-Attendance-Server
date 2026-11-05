package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 입·퇴장 현황 조회 결과 공유 설정(#255).
 *
 * @property ttlMillis 같은 박람회·같은 조건의 현황 조회 결과를 이 시간 동안 여러 대시보드가 함께 쓴다. 0이면 끈다.
 * 관리자 변경 신호(`admin-events.enabled`)가 켜져 있으면 변경 신호를 받는 즉시 그 박람회 결과를 버린다.
 */
@ConfigurationProperties(prefix = "admin-stats-cache")
data class AdminStatsCacheProperties(
    val ttlMillis: Long = 1000,
)
