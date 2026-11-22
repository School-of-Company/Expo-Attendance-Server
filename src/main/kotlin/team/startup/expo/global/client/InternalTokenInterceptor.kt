package team.startup.expo.global.client

import feign.RequestInterceptor
import team.startup.expo.global.security.InternalTokenAuthenticationFilter

/**
 * 다른 서비스의 `/internal` 하위 경로를 부를 때 [token]을 `X-Internal-Token` 헤더에 싣는다.
 * 각 Feign 클라이언트 설정(`@Configuration` 없음)에서만 빈으로 만들어, 그 클라이언트에만 해당 서비스의 토큰이 실리게 한다.
 */
fun internalTokenInterceptor(token: String): RequestInterceptor =
    RequestInterceptor { template -> template.header(InternalTokenAuthenticationFilter.TOKEN_HEADER, token) }
