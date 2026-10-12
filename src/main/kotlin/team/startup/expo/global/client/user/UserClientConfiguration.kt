package team.startup.expo.global.client.user

import feign.RequestInterceptor
import org.springframework.context.annotation.Bean
import team.startup.expo.global.logging.RequestIdFeignInterceptor

/**
 * `@Configuration`을 붙이지 않는다. 붙이면 컴포넌트 스캔으로 전역에 등록되어 다른 Feign 클라이언트에도
 * 내부 토큰이 실려 나간다. 이 클래스는 [UserClient]에만 적용된다.
 */
class UserClientConfiguration {
    @Bean
    fun userInternalTokenInterceptor(properties: UserClientProperties): RequestInterceptor =
        RequestInterceptor { template -> template.header(TOKEN_HEADER, properties.internalToken) }

    /** 요청 상관 ID를 헤더로 넘긴다. 비밀 값이 없지만 같은 원칙으로 이 클라이언트에만 적용한다. */
    @Bean
    fun requestIdInterceptor(): RequestInterceptor = RequestIdFeignInterceptor()

    private companion object {
        const val TOKEN_HEADER = "X-Internal-Token"
    }
}
