package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 입장 스캔 결과 재생 설정(#176).
 *
 * @property windowSeconds 입장이 기록된 뒤 이 시간 안에 같은 QR을 같은 스캐너로 다시 찍으면 400 대신 직전 입장 결과를 다시
 * 돌려준다(타임아웃으로 응답을 못 받은 스캐너의 재시도). 0이면 끈다.
 */
@ConfigurationProperties(prefix = "entry-replay")
data class EntryReplayProperties(
    val windowSeconds: Long = 30,
)
