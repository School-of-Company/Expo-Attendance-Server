package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 입구 스캔의 재시도 허용 설정.
 *
 * @property windowSeconds 입장이 성공한 뒤 같은 스캐너 사용자가 같은 QR을 다시 찍으면 "이미 입장" 대신 처음 응답을 돌려주는
 * 시간(응답이 유실돼 명찰을 못 받은 경우를 위한 것). 0이면 끈다.
 */
@ConfigurationProperties(prefix = "entry-retry")
data class EntryRetryProperties(
    val windowSeconds: Long = 300,
)
