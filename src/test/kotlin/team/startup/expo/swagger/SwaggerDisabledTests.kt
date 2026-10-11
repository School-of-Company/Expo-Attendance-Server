package team.startup.expo.swagger

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

/** 설정을 건드리지 않으면(`SWAGGER_ENABLED` 없음) 문서를 제공하지 않는다. 열어 둔 규칙이 없어 보안 설정이 401로 막는다. */
class SwaggerDisabledTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `기본 설정에서는 API 문서를 제공하지 않는다`() {
        mockMvc.perform(get("/attendance/v3/api-docs")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/attendance/v3/api-docs/swagger-config")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `기본 설정에서는 Swagger UI 화면을 제공하지 않는다`() {
        mockMvc.perform(get("/attendance/swagger-ui.html")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/attendance/swagger-ui/index.html")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `기본 설정에서는 springdoc 기본 경로의 문서도 열리지 않는다`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized)
    }
}
