package team.startup.expo.swagger

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

/** `swagger.include-internal=true`: 내부 경로가 `X-Internal-Token` 보안 스키마와 함께 문서에 나온다. */
@TestPropertySource(properties = ["swagger.enabled=true", "swagger.include-internal=true"])
class SwaggerIncludeInternalTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `내부 경로가 토큰 보안 스키마와 함께 나온다`() {
        val qr = "$.paths['/internal/expos/{expoId}/participants/{participantId}/qr'].post"
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("/internal/expos/{expoId}/participants/{participantId}/qr")))
            .andExpect(jsonPath(qr).exists())
            .andExpect(jsonPath("$.components.securitySchemes.internalToken.name").value("X-Internal-Token"))
            .andExpect(jsonPath("$.components.securitySchemes.internalToken.in").value("header"))
            .andExpect(jsonPath("$qr.security[0].internalToken").exists())
            .andExpect(jsonPath("$qr.responses['401']").exists())
            .andExpect(jsonPath("$.components.schemas.ParticipantQrResDto.properties.code.description").exists())
    }

    @Test
    fun `취소 보호의 응답 상태가 실제와 같이 문서에 있다`() {
        val operation = "$.paths['/internal/expos/{expoId}/participants/{participantId}/cancellation-guard/{operationId}'].put"
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$operation.responses['204']").exists())
            .andExpect(jsonPath("$operation.responses['200']").doesNotExist())
            .andExpect(jsonPath("$operation.responses['409']").exists())
    }

    @Test
    fun `공개 경로도 함께 나온다`() {
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/attendance/{expoId}'].patch").exists())
    }
}
