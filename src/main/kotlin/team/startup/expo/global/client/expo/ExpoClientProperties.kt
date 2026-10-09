package team.startup.expo.global.client.expo

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 박람회 서비스를 호출할 때 보내는 내부 토큰이다. 박람회 서비스의 `INTERNAL_TOKEN`과 같은 값이어야 한다.
 * `url`은 비워 두면 Eureka의 `expo-expo-server`로 찾고, 로컬에서 Gateway와 Eureka 없이 부를 때만 지정한다.
 */
@ConfigurationProperties(prefix = "clients.expo")
data class ExpoClientProperties(
    val url: String = "",
    val internalToken: String,
) {
    // data class의 기본 toString은 토큰을 그대로 출력한다
    override fun toString() = "ExpoClientProperties(url=$url, internalToken=***)"

    init {
        require(internalToken.length >= MIN_TOKEN_LENGTH) { "clients.expo.internal-token must be at least $MIN_TOKEN_LENGTH characters" }
    }

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
    }
}
