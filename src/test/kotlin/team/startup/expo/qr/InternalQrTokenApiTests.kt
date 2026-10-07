package team.startup.expo.qr

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

class InternalQrTokenApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_qr_entry, tb_qr_token RESTART IDENTITY CASCADE")
        saveToken(ENTERED, EXPO)
        saveToken(NOT_ENTERED, EXPO)
        jdbcTemplate.update("INSERT INTO tb_qr_entry (token, attendance_date, entered_at) VALUES (?, now(), now())", ENTERED)
    }

    @Test
    fun `입장이 확인된 토큰은 박람회 id를 돌려준다`() {
        resolve(ENTERED)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expoId").value(EXPO))
    }

    @Test
    fun `입장 전 토큰과 없는 토큰은 똑같이 404이다`() {
        val notEntered =
            resolve(NOT_ENTERED)
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString
        val missing =
            resolve("missing-token")
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString

        // 응답이 같아야 토큰의 존재 여부가 드러나지 않는다
        check(notEntered == missing)
    }

    @Test
    fun `토큰이 비었거나 64자를 넘으면 400이다`() {
        resolve("").andExpect(status().isBadRequest)
        resolve("a".repeat(65)).andExpect(status().isBadRequest)
    }

    @Test
    fun `내부 토큰이 없거나 틀리면 401이다`() {
        mockMvc
            .perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""{"token":"$ENTERED"}"""))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                post(PATH)
                    .header("X-Internal-Token", "wrong-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$ENTERED"}"""),
            ).andExpect(status().isUnauthorized)
    }

    private fun saveToken(
        token: String,
        expoId: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES (?, ?, 'STANDARD', now())",
            token,
            expoId,
        )
    }

    private fun resolve(token: String): ResultActions =
        mockMvc.perform(
            post(PATH)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}"""),
        )

    private companion object {
        const val PATH = "/internal/qr-tokens/resolve"
        const val EXPO = "11111111-1111-1111-1111-111111111111"
        const val ENTERED = "entered-token"
        const val NOT_ENTERED = "not-entered-token"
    }
}
