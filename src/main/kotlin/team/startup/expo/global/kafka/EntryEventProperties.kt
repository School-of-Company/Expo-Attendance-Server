package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** 입장 이벤트를 발행하는 토픽 이름. 문자 서비스가 구독한다. */
@ConfigurationProperties(prefix = "entry-events")
data class EntryEventProperties(
    val recordedTopic: String,
)
