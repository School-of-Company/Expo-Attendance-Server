package team.startup.expo.global.client.user

import org.springframework.boot.context.properties.ConfigurationProperties
import team.startup.expo.global.security.InternalTokenPolicy

/**
 * 유저 서비스를 호출할 때 보내는 내부 토큰이다. 유저 서비스의 `INTERNAL_TOKEN`과 같은 값이어야 한다.
 * `url`은 비워 두면 Eureka의 `expo-user-server`로 찾고, 로컬에서 Gateway와 Eureka 없이 부를 때만 지정한다.
 */
@ConfigurationProperties(prefix = "clients.user")
data class UserClientProperties(
    val url: String = "",
    val internalToken: String,
) {
    override fun toString() = "UserClientProperties(url=$url, internalToken=${InternalTokenPolicy.MASKED})"

    init {
        InternalTokenPolicy.requireValid(internalToken, "clients.user.internal-token")
    }
}
