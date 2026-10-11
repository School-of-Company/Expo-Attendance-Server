package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.global.client.user.ParticipantQrReqDto
import team.startup.expo.global.client.user.ParticipantQrResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport

class ParticipantQrApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var userClient: UserClient

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_preregister_session RESTART IDENTITY CASCADE")
        reset(userClient)
        doReturn(
            ParticipantQrResDto(PARTICIPANT, CODE),
        ).`when`(userClient).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))
    }

    @Test
    fun `확정된 참가자의 QR 값을 돌려준다`() {
        insert("ACTIVE")

        lookup()
            .andExpect(
                status().isOk,
            ).andExpect(jsonPath("$.participantId").value(PARTICIPANT))
            .andExpect(jsonPath("$.code").value(CODE))

        verify(userClient).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))
    }

    @Test
    fun `회차 기록이 없거나 취소된 참가자는 유저 서비스를 부르지 않고 같은 404이다`() {
        val noRecord =
            lookup()
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString

        insert("CANCELLED")
        val cancelled =
            lookup()
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString

        listOf(noRecord, cancelled).toSet().size shouldBe 1
        verify(userClient, never()).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))
    }

    @Test
    fun `유저 서비스가 모른다고 하면 소유권이 다른 경우와 구분 없이 404이다`() {
        insert("ACTIVE")
        doThrow(feignException(404)).`when`(userClient).getParticipantQr(ParticipantQrReqDto(EXPO, OTHER_REPRESENTATIVE, PARTICIPANT))

        val notOwner =
            lookup(OTHER_REPRESENTATIVE)
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString
        jdbcTemplate.update("DELETE FROM tb_preregister_session")
        val noRecord =
            lookup(OTHER_REPRESENTATIVE)
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString

        listOf(notOwner, noRecord).toSet().size shouldBe 1
    }

    @Test
    fun `유저 서비스가 다른 참가자의 값을 돌려주면 내주지 않는다`() {
        insert("ACTIVE")
        doReturn(
            ParticipantQrResDto(PARTICIPANT + 1, CODE),
        ).`when`(userClient).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))

        lookup()
            .andExpect(
                status().isNotFound,
            ).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(CODE))))
    }

    @Test
    fun `유저 서비스 장애는 없음이 아니라 503이고 QR 값을 내지 않는다`() {
        insert("ACTIVE")
        doThrow(feignException(500)).`when`(userClient).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))

        lookup()
            .andExpect(
                status().isServiceUnavailable,
            ).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(CODE))))
    }

    @Test
    fun `내부 토큰이 필요하고 대표자 ID를 검증한다`() {
        insert("ACTIVE")
        mockMvc
            .perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""{"representativeId":$REPRESENTATIVE}"""))
            .andExpect(status().isUnauthorized)

        listOf("""{}""", """{"representativeId":0}""", """{"representativeId":"x"}""").forEach {
            mockMvc
                .perform(post(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }
        verify(userClient, never()).getParticipantQr(ParticipantQrReqDto(EXPO, REPRESENTATIVE, PARTICIPANT))
    }

    private fun insert(status: String) {
        jdbcTemplate.update(
            "INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, updated_at) VALUES (?, ?, 11, ?, now())",
            EXPO,
            PARTICIPANT,
            status,
        )
    }

    private fun lookup(representativeId: Long = REPRESENTATIVE): ResultActions =
        mockMvc.perform(
            post(PATH)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"representativeId":$representativeId}"""),
        )

    private fun feignException(status: Int): FeignException =
        FeignException.errorStatus(
            "call",
            feign.Response
                .builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.POST, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate()))
                .headers(emptyMap())
                .build(),
        )

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10"
        const val PARTICIPANT = 7001L
        const val REPRESENTATIVE = 7000L
        const val OTHER_REPRESENTATIVE = 9000L
        const val CODE = "codePPPPPPPPPPPPPPPPPP"
        const val PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/qr"
    }
}
