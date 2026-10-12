package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 중단된 취소 작업 기록(`tb_cancellation_aborted`)의 보관 설정.
 *
 * @property days 이 일수보다 오래된 기록을 주기적으로 지운다. 0 이하이면 지우지 않는다(기본). 기록은 중단된 뒤 지연되어 도착한
 * 같은 operationId의 acquire가 보호를 다시 걸지 못하게 하므로, 신청 서비스의 재시도·지연 한도보다 충분히 길게 둔다.
 */
@ConfigurationProperties(prefix = "cancellation-aborted-retention")
data class CancellationAbortedRetentionProperties(
    val days: Int = 0,
) {
    val enabled: Boolean get() = days > 0
}
