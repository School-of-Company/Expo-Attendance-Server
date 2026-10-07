package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** 입장 이벤트를 발행하는 토픽 이름과 아웃박스 릴레이 설정. 문자 서비스가 토픽을 구독한다. */
@ConfigurationProperties(prefix = "entry-events")
data class EntryEventProperties(
    val recordedTopic: String,
    val relay: Relay = Relay(),
) {
    /**
     * @property enabled 끄면 발행하지 않고 이벤트가 `PENDING`으로 쌓이기만 한다.
     * @property intervalMs 발행 주기. 앞선 실행이 끝난 뒤부터 센다.
     * @property batchSize 한 번에 발행하는 최대 이벤트 수.
     * @property sendTimeoutMs 이벤트 하나의 브로커 응답을 기다리는 시간.
     * @property retentionDays 발행을 마친 이벤트를 보관하는 기간. 전화번호가 들어 있어 오래 두지 않는다.
     */
    data class Relay(
        val enabled: Boolean = true,
        val intervalMs: Long = 3_000,
        val batchSize: Int = 100,
        val sendTimeoutMs: Long = 5_000,
        val retentionDays: Long = 7,
    )
}
