package team.startup.expo.global.logging

import feign.RequestInterceptor
import feign.RequestTemplate

/**
 * 지금 요청의 상관 ID를 다른 서비스 호출 헤더에 싣는다. 비밀 값을 담지 않지만 내부 토큰 인터셉터와 같은 원칙으로,
 * `@Configuration`/`@Component`로 등록하지 않고 각 Feign 클라이언트 설정에서만 빈으로 만든다.
 */
class RequestIdFeignInterceptor : RequestInterceptor {
    override fun apply(template: RequestTemplate) {
        RequestContext.requestId()?.let { template.header(RequestIdFilter.HEADER, it) }
    }
}
