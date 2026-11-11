package team.startup.expo.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 서비스 간 호출(`/internal` 하위 경로)을 인증하는 공유 시크릿이다. 값은 코드에 두지 않고 배포 설정으로 받는다.
 * 길이 검사와 값 가리기는 [InternalTokenPolicy]를 따른다.
 */
@ConfigurationProperties(prefix = "internal")
data class InternalProperties(
    val token: String,
) {
    override fun toString() = "InternalProperties(token=${InternalTokenPolicy.MASKED})"

    init {
        InternalTokenPolicy.requireValid(token, "internal.token")
    }
}
