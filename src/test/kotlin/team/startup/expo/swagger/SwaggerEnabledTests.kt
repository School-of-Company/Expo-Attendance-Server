package team.startup.expo.swagger

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

/** `swagger.enabled=true`: 공개 경로만 문서에 나오고, 내부 경로(`/internal` 하위)는 나오지 않는다. */
@TestPropertySource(properties = ["swagger.enabled=true"])
class SwaggerEnabledTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `API 문서에 공개 경로가 나온다`() {
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("Expo Attendance Server API"))
            .andExpect(jsonPath("$.paths['/attendance/{expoId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/attendance/standard/{programId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/attendance/training/{programId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/attendance/qr/{expoId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/qr-tokens/{expoId}'].post").exists())
    }

    @Test
    fun `API 문서에 내부 경로와 내부 전용 스키마가 나오지 않는다`() {
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            // 소개 글에는 `/internal/**` 설명이 있어 경로 키로 확인한다
            .andExpect(jsonPath("$.paths['/internal/expos/{expoId}/participants/{participantId}/qr']").doesNotExist())
            .andExpect(jsonPath("$.paths['/internal/program-attendances/standard/{programId}']").doesNotExist())
            .andExpect(jsonPath("$.paths['/internal/expos/{expoId}']").doesNotExist())
            .andExpect(content().string(not(containsString("\"/internal/"))))
            .andExpect(content().string(not(containsString("ParticipantQrResDto"))))
            .andExpect(jsonPath("$.components.securitySchemes").doesNotExist())
    }

    @Test
    fun `오류 응답 스키마와 엔드포인트별 오류 상태가 문서에 있다`() {
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.status").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists())
            .andExpect(jsonPath("$.paths['/attendance/{expoId}'].patch.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/attendance/{expoId}'].patch.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/attendance/{expoId}'].patch.responses['503']").exists())
            .andExpect(jsonPath("$.paths['/attendance/qr/{expoId}'].patch.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/qr-tokens/{expoId}'].post.responses['403']").exists())
            .andExpect(
                jsonPath("$.paths['/attendance/{expoId}'].patch.responses['404'].content['application/json'].schema.\$ref")
                    .value("#/components/schemas/ErrorResponse"),
            )
    }

    @Test
    fun `DTO 필드에 설명과 예시가 붙는다`() {
        mockMvc
            .perform(get("/attendance/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.schemas.ScanEntryReqDto.properties.authority.description").exists())
            .andExpect(jsonPath("$.components.schemas.ScanQrTokenReqDto.properties.token.example").value("sample-token-not-real"))
    }

    @Test
    fun `Swagger UI 진입 화면이 응답한다`() {
        mockMvc.perform(get("/attendance/swagger-ui.html")).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("/attendance/swagger-ui/index.html")).andExpect(status().isOk)
        mockMvc
            .perform(get("/attendance/v3/api-docs/swagger-config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value("/attendance/v3/api-docs"))
    }

    @Test
    fun `문서를 열어도 다른 경로와 다른 메서드는 열리지 않는다`() {
        mockMvc.perform(get("/attendance/anything")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(patch("/attendance/v3/api-docs").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().is4xxClientError)
    }
}
