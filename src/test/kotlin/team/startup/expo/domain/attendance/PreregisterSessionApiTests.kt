package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class PreregisterSessionApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var deleteExpoDataService: DeleteExpoDataService

    @MockitoBean
    private lateinit var userClient: UserClient

    @MockitoBean
    private lateinit var expoClient: ExpoClient

    private val now get() = clock.instant()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_preregister_session, tb_entry_outbox, tb_deleted_expo RESTART IDENTITY CASCADE")
        reset(userClient, expoClient)
        val today = LocalDate.now(clock)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        doReturn(
            entry(PARTICIPANT),
        ).`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
    }

    @Test
    fun `회차 기록이 없는 참가자는 회차 확인 없이 입장한다`() {
        scan().andExpect(status().isOk)

        verify(expoClient, never()).getPreregisterSession(anyString(), anyLong())
    }

    @Test
    fun `회차 시작 30분 전부터 종료 시각까지는 입장한다`() {
        assign(SESSION).andExpect(status().isNoContent)

        // 시작 29분 전
        sessionAt(start = now.plus(Duration.ofMinutes(29)), end = now.plus(Duration.ofHours(3)))
        scan().andExpect(status().isOk)

        // 이미 시작해 진행 중, 종료 직전
        sessionAt(start = now.minus(Duration.ofHours(2)), end = now.plus(Duration.ofSeconds(30)))
        scan().andExpect(status().isOk)
    }

    @Test
    fun `이른 입장과 지난 회차는 다른 문구로 거부하고 입장을 기록하지 않는다`() {
        assign(SESSION).andExpect(status().isNoContent)

        sessionAt(start = now.plus(Duration.ofMinutes(31)), end = now.plus(Duration.ofHours(3)))
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다."))

        sessionAt(start = now.minus(Duration.ofHours(3)), end = now.minus(Duration.ofSeconds(1)))
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("신청한 회차가 이미 끝났습니다."))

        verify(userClient, never()).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
    }

    @Test
    fun `취소된 신청의 QR은 거부하고 재신청해 확정되면 다시 입장한다`() {
        assign(SESSION).andExpect(status().isNoContent)
        sessionAt(start = now, end = now.plus(Duration.ofHours(3)))

        cancel().andExpect(status().isNoContent)
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청의 QR입니다."))
        verify(userClient, never()).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))

        // 취소를 두 번 보내도 같고, 재신청해 확정되면 다시 입장할 수 있다
        cancel().andExpect(status().isNoContent)
        assign(SESSION).andExpect(status().isNoContent)
        scan().andExpect(status().isOk)
    }

    @Test
    fun `회차를 바꿔 기록하면 새 회차 시간으로 확인한다`() {
        assign(SESSION).andExpect(status().isNoContent)
        assign(OTHER_SESSION).andExpect(status().isNoContent)

        jdbcTemplate.queryForObject(
            "SELECT session_id FROM tb_preregister_session WHERE participant_id = ?",
            Long::class.java,
            PARTICIPANT,
        ) shouldBe
            OTHER_SESSION
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_preregister_session", Long::class.java) shouldBe 1L
    }

    @Test
    fun `박람회 서비스가 회차를 못 주면 없음이 아니라 503이다`() {
        assign(SESSION).andExpect(status().isNoContent)

        listOf(404, 409, 500).forEach { code ->
            doThrow(feignException(code)).`when`(expoClient).getPreregisterSession(EXPO, SESSION)
            scan().andExpect(status().isServiceUnavailable)
        }
        verify(userClient, never()).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
    }

    @Test
    fun `전화번호로 입장하는 이전 방식과 연수자는 회차를 확인하지 않는다`() {
        assign(SESSION).andExpect(status().isNoContent)
        doReturn(entry(PARTICIPANT)).`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", phoneNumber = "01012345678"))

        mockMvc
            .perform(
                patch(
                    "/attendance/$EXPO",
                ).contentType(MediaType.APPLICATION_JSON).content("""{"authority":"ROLE_STANDARD","phoneNumber":"01012345678"}"""),
            ).andExpect(status().isOk)

        verify(expoClient, never()).getPreregisterSession(anyString(), anyLong())
    }

    @Test
    fun `회차 기록 API는 내부 토큰이 필요하고 입력과 삭제된 박람회를 검증한다`() {
        mockMvc
            .perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("""{"sessionId":$SESSION}"""))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized)

        listOf("""{}""", """{"sessionId":0}""", """{"sessionId":-1}""", """{"sessionId":"x"}""").forEach {
            mockMvc
                .perform(put(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }

        deleteExpoDataService.delete(EXPO)
        assign(SESSION).andExpect(status().isNotFound)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_preregister_session", Long::class.java) shouldBe 0L
    }

    @Test
    fun `취소 표시는 기록이 없어도 성공하고 아무것도 만들지 않는다`() {
        cancel().andExpect(status().isNoContent)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_preregister_session", Long::class.java) shouldBe 0L
    }

    @Test
    fun `박람회를 삭제하면 그 박람회의 회차 기록도 지운다`() {
        assign(SESSION).andExpect(status().isNoContent)
        jdbcTemplate.update(
            "INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, updated_at) VALUES ('other-expo', 1, 1, 'ACTIVE', now())",
        )

        deleteExpoDataService.delete(EXPO)

        jdbcTemplate.queryForList("SELECT expo_id FROM tb_preregister_session", String::class.java) shouldBe listOf("other-expo")
    }

    private fun sessionAt(
        start: Instant,
        end: Instant,
    ) {
        doReturn(PreregisterSessionResDto(SESSION, start, end)).`when`(expoClient).getPreregisterSession(EXPO, SESSION)
    }

    private fun scan(): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","participantId":$PARTICIPANT,"code":"$CODE"}"""),
        )

    private fun assign(sessionId: Long): ResultActions =
        mockMvc.perform(
            put(
                PATH,
            ).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content("""{"sessionId":$sessionId}"""),
        )

    private fun cancel(): ResultActions = mockMvc.perform(delete(PATH).header("X-Internal-Token", INTERNAL_TOKEN))

    private fun entry(id: Long) = RecordEntryResDto(id, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null)

    private fun feignException(status: Int): FeignException =
        FeignException.errorStatus(
            "call",
            feign.Response
                .builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate()))
                .headers(emptyMap())
                .build(),
        )

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10"
        const val PARTICIPANT = 7001L
        const val CODE = "codePPPPPPPPPPPPPPPPPP"
        const val SESSION = 11L
        const val OTHER_SESSION = 12L
        const val PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session"
    }
}
