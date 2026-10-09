package team.startup.expo.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 서비스 간 호출(`/internal` 하위 경로)을 인증하는 공유 시크릿이다. 값은 코드에 두지 않고 배포 설정으로 받는다.
 * 짧은 값은 추측하기 쉬우므로 기동 시 거부하며, 오류 메시지에 값을 싣지 않는다.
 */
@ConfigurationProperties(prefix = "internal")
data class InternalProperties(
    val token: String,
) {
    // data class의 기본 toString은 토큰을 그대로 출력한다
    override fun toString() = "InternalProperties(token=***)"

    init {
        require(token.length >= MIN_TOKEN_LENGTH) { "internal.token must be at least $MIN_TOKEN_LENGTH characters" }
    }

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
    }
}
