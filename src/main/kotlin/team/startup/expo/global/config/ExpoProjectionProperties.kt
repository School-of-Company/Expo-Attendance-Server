package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 박람회 프로젝션 설정(#240, `expo.expo.changed`). 기본은 꺼져 있다.
 *
 * @property enabled 켜면 consumer가 프로젝션을 채우고, 박람회 기간·프로그램·회차 조회가 프로젝션을 먼저 본다. 프로젝션에 없으면
 * 박람회 서비스를 동기로 부른다. 끄면 consumer가 뜨지 않고 지금처럼 항상 동기로 부른다. 켜려면 `KAFKA_BOOTSTRAP_SERVERS`가 있어야 한다.
 * @property groupId consumer group.
 */
@ConfigurationProperties(prefix = "expo-projection")
data class ExpoProjectionProperties(
    val enabled: Boolean = false,
    val groupId: String = "expo-attention-server.expo-changed",
)
