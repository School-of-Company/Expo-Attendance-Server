package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 사전등록 입장 시간 설정.
 *
 * @property leadMinutes 회차 시작 몇 분 전부터 입장할 수 있는지. 종료 시각까지는 항상 입장할 수 있다.
 */
@ConfigurationProperties(prefix = "preregister-entry")
data class PreregisterEntryProperties(
    val leadMinutes: Long = 30,
)
