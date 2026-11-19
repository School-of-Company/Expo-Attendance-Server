package team.startup.expo.support

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

/** 게이트웨이를 거친 관리자 요청. ExpoClient 목의 admin-access 기본 응답은 성공이다. */
fun MockHttpServletRequestBuilder.withExpoAdmin(): MockHttpServletRequestBuilder =
    header("X-User-Id", "1")
        .header("X-User-Role", "ROLE_ADMIN")
        .header("Authorization", "Bearer test-admin")
