package team.startup.expo.global.security

/**
 * 내부 토큰(`internal.token`, `clients.*.internal-token`) 설정의 공통 규칙이다. 짧은 값은 추측하기 쉬우므로 기동 시 거부하며,
 * 오류 메시지와 `toString`에 값을 싣지 않는다.
 */
object InternalTokenPolicy {
    const val MIN_LENGTH = 32

    /** 설정 클래스의 `toString`에서 토큰 자리에 대신 쓴다(data class의 기본 `toString`은 토큰을 그대로 출력한다). */
    const val MASKED = "***"

    /** [token]이 [MIN_LENGTH]자보다 짧으면 [propertyName]만 담은 메시지로 기동을 막는다. */
    fun requireValid(
        token: String,
        propertyName: String,
    ) {
        require(token.length >= MIN_LENGTH) { "$propertyName must be at least $MIN_LENGTH characters" }
    }
}
