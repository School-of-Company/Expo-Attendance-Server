package team.startup.expo.global.client.application

import org.springframework.boot.context.properties.ConfigurationProperties
import team.startup.expo.global.security.InternalTokenPolicy

/**
 * 신청 서비스를 호출할 때 보내는 내부 토큰이다. 신청 서비스의 `INTERNAL_TOKEN`과 같은 값이어야 한다.
 * `url`은 비워 두면 Eureka의 `expo-application-server`로 찾고, 로컬에서 Gateway와 Eureka 없이 부를 때만 지정한다.
 */
@ConfigurationProperties(prefix = "clients.application")
data class ApplicationClientProperties(
    val url: String = "",
    val internalToken: String,
) {
    override fun toString() = "ApplicationClientProperties(url=$url, internalToken=${InternalTokenPolicy.MASKED})"

    init {
        InternalTokenPolicy.requireValid(internalToken, "clients.application.internal-token")
    }
}
