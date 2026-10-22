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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.support.FieldVisitFixtures
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Import(PreregisterSessionApiTests.FixedClockConfig::class)
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
        FieldVisitFixtures.clear(jdbcTemplate)
        jdbcTemplate.execute("TRUNCATE TABLE tb_preregister_session, tb_cancellation_guard, tb_deleted_expo RESTART IDENTITY CASCADE")
        reset(userClient, expoClient)
        doReturn(ResolveStandardParticipantResDto(PARTICIPANT))
            .`when`(userClient)
            .resolveStandardParticipant(ResolveStandardParticipantReqDto(EXPO, "01012345678"))
        val today = LocalDate.now(clock)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        doReturn(
            entry(PARTICIPANT),
        ).`when`(
            userClient,
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
        doReturn(
            entry(PARTICIPANT),
        ).`when`(
            userClient,
        ).recordEntry(
            RecordEntryReqDto(
                EXPO,
                "STANDARD",
                phoneNumber = "01012345678",
                preregisterSessionId = SESSION,
                attendanceDate = LocalDate.now(clock),
            ),
        )
        // 회차 입장 시간 안의 QR 입장은 확인한 회차를 함께 보낸다(#142)
        listOf(SESSION, OTHER_SESSION).forEach { session ->
            doReturn(entry(PARTICIPANT)).`when`(userClient).recordEntry(withSession(session))
        }
    }

    private fun withSession(session: Long) =
        RecordEntryReqDto(
            EXPO,
            "STANDARD",
            participantId = PARTICIPANT,
            code = CODE,
            preregisterSessionId = session,
            attendanceDate = LocalDate.now(clock),
        )

    @Test
    fun `날짜별 등록도 회차도 없는 참가자는 오늘 권한이 없다`() {
        scan().andExpect(status().isBadRequest)

        verify(expoClient, never()).getPreregisterSession(anyString(), anyLong())
        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
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

        verify(userClient, times(2)).recordEntry(withSession(SESSION))
        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
    }

    @Test
    fun `이른 입장과 지난 회차는 다른 문구로 거부하고 입장을 기록하지 않는다`() {
        assign(SESSION).andExpect(status().isNoContent)

        sessionAt(start = now.plus(Duration.ofMinutes(31)), end = now.plus(Duration.ofHours(3)))
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다."))

        sessionAt(start = now.minus(Duration.ofHours(3)), end = now.minus(Duration.ofSeconds(1)))
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("신청한 회차가 이미 끝났습니다."))

        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
        verify(userClient, never()).recordEntry(withSession(SESSION))
    }

    @Test
    fun `취소된 신청의 QR은 거부하고 재신청해 확정되면 다시 입장한다`() {
        assign(SESSION).andExpect(status().isNoContent)
        sessionAt(start = now, end = now.plus(Duration.ofHours(3)))

        cancel().andExpect(status().isNoContent)
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
        mockMvc
            .perform(
                patch("/attendance/$EXPO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","phoneNumber":"01012345678"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("취소된 신청입니다."))
        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
        verify(userClient, never()).recordEntry(withSession(SESSION))
        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", phoneNumber = "01012345678", attendanceDate = LocalDate.now(clock)))

        // 취소를 두 번 보내도 같고, 재신청해 확정되면 다시 입장할 수 있다
        cancel().andExpect(status().isNoContent)
        assign(SESSION).andExpect(status().isNoContent)
        scan().andExpect(status().isOk)
        verify(userClient).recordEntry(withSession(SESSION))
    }

    @Test
    fun `두 회차를 배정하면 오전과 오후에 각각 그 회차로 입장한다`() {
        assign(SESSION).andExpect(status().isNoContent)
        assign(OTHER_SESSION).andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_preregister_session", Long::class.java) shouldBe 2L

        // 오전 회차 시간
        sessionAt(start = now.minus(Duration.ofMinutes(10)), end = now.plus(Duration.ofHours(1)))
        sessionAt(start = now.plus(Duration.ofHours(4)), end = now.plus(Duration.ofHours(6)), sessionId = OTHER_SESSION)
        scan().andExpect(status().isOk)
        verify(userClient).recordEntry(withSession(SESSION))

        // 오후 회차 시간
        sessionAt(start = now.minus(Duration.ofHours(6)), end = now.minus(Duration.ofHours(4)))
        sessionAt(start = now.minus(Duration.ofMinutes(10)), end = now.plus(Duration.ofHours(1)), sessionId = OTHER_SESSION)
        scan().andExpect(status().isOk)
        verify(userClient).recordEntry(withSession(OTHER_SESSION))
    }

    @Test
    fun `회차 하나를 취소해도 다른 회차로는 입장한다`() {
        assign(SESSION, revision = 1).andExpect(status().isNoContent)
        assign(OTHER_SESSION, revision = 2).andExpect(status().isNoContent)
        mockMvc
            .perform(delete("$PATH?sessionId=$SESSION&revision=3").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isNoContent)

        // 취소한 오전 회차 시간에는 거절한다
        sessionAt(start = now.minus(Duration.ofMinutes(10)), end = now.plus(Duration.ofHours(1)))
        sessionAt(start = now.plus(Duration.ofHours(4)), end = now.plus(Duration.ofHours(6)), sessionId = OTHER_SESSION)
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다."))

        // 남은 오후 회차 시간에는 입장한다
        sessionAt(start = now.minus(Duration.ofMinutes(10)), end = now.plus(Duration.ofHours(1)), sessionId = OTHER_SESSION)
        scan().andExpect(status().isOk)
        verify(userClient, never()).recordEntry(withSession(SESSION))
        verify(userClient).recordEntry(withSession(OTHER_SESSION))

        // 회차를 지정하지 않은 취소는 모든 회차를 취소하고, 그보다 오래된 회차 PUT은 되살리지 못한다
        cancel(revision = 4).andExpect(status().isNoContent)
        assign(SESSION, revision = 3).andExpect(status().isNoContent)
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
    }

    @Test
    fun `박람회 서비스가 회차를 못 주면 없음이 아니라 503이다`() {
        assign(SESSION).andExpect(status().isNoContent)

        listOf(404, 409, 500).forEach { code ->
            doThrow(feignException(code)).`when`(expoClient).getPreregisterSession(EXPO, SESSION)
            scan().andExpect(status().isServiceUnavailable)
        }
        verify(
            userClient,
            never(),
        ).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, attendanceDate = LocalDate.now(clock)))
        verify(userClient, never()).recordEntry(withSession(SESSION))
    }

    @Test
    fun `잘못된 code는 취소·이른 입장·지난 회차 어느 상태에서도 같은 404이고 신청 상태를 알리지 않는다`() {
        assign(SESSION).andExpect(status().isNoContent)
        doThrow(
            feignException(404),
        ).`when`(userClient).verifyStandardParticipant(VerifyStandardParticipantReqDto(EXPO, PARTICIPANT, WRONG_CODE))

        val responses =
            listOf(
                // 이른 입장
                { sessionAt(start = now.plus(Duration.ofHours(2)), end = now.plus(Duration.ofHours(5))) },
                // 지난 회차
                { sessionAt(start = now.minus(Duration.ofHours(5)), end = now.minus(Duration.ofHours(2))) },
                // 취소
                { cancel() },
            ).map { prepare ->
                prepare()
                scan(WRONG_CODE)
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.message").value("행사 참가자를 찾지 못 했습니다."))
                    .andReturn()
                    .response.contentAsString
            }

        responses.toSet().size shouldBe 1
        verify(
            userClient,
            never(),
        ).recordEntry(
            RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = WRONG_CODE, attendanceDate = LocalDate.now(clock)),
        )
    }

    @Test
    fun `유저 서비스가 코드를 확인하지 못하면 신청 상태를 알리지 않고 503이다`() {
        assign(SESSION).andExpect(status().isNoContent)
        doThrow(feignException(500)).`when`(userClient).verifyStandardParticipant(VerifyStandardParticipantReqDto(EXPO, PARTICIPANT, CODE))

        scan().andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `회차 기록이 없어도 먼저 코드를 확인하고 날짜 미상 입장을 거절한다`() {
        scan().andExpect(status().isBadRequest)

        verify(userClient).verifyStandardParticipant(VerifyStandardParticipantReqDto(EXPO, PARTICIPANT, CODE))
    }

    @Test
    fun `회차 시간 밖에도 전화번호 입장은 오늘 회차 ID로 기록한다`() {
        assign(SESSION).andExpect(status().isNoContent)
        sessionAt(start = now.plus(Duration.ofHours(3)), end = now.plus(Duration.ofHours(5)))

        mockMvc
            .perform(
                patch(
                    "/attendance/$EXPO",
                ).contentType(MediaType.APPLICATION_JSON).content("""{"authority":"ROLE_STANDARD","phoneNumber":"01012345678"}"""),
            ).andExpect(status().isOk)

        verify(
            userClient,
        ).recordEntry(
            RecordEntryReqDto(
                EXPO,
                "STANDARD",
                phoneNumber = "01012345678",
                preregisterSessionId = SESSION,
                attendanceDate = LocalDate.now(clock),
            ),
        )
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
    fun `늦게 도착한 오래된 취소는 재신청 확정을 되돌리지 못한다`() {
        assign(SESSION, revision = 1).andExpect(status().isNoContent)
        cancel(revision = 2).andExpect(status().isNoContent)
        assign(SESSION, revision = 3).andExpect(status().isNoContent)

        // 타임아웃됐던 revision 2 취소가 뒤늦게 다시 도착한다
        cancel(revision = 2).andExpect(status().isNoContent)

        stored() shouldBe ("ACTIVE" to 3L)
        sessionAt(start = now, end = now.plus(Duration.ofHours(3)))
        scan().andExpect(status().isOk)
        verify(userClient).recordEntry(withSession(SESSION))
    }

    @Test
    fun `늦게 도착한 오래된 확정은 취소를 되돌리지 못한다`() {
        assign(SESSION, revision = 1).andExpect(status().isNoContent)
        cancel(revision = 2).andExpect(status().isNoContent)

        assign(SESSION, revision = 1).andExpect(status().isNoContent)

        stored() shouldBe ("CANCELLED" to 2L)
        scan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
    }

    @Test
    fun `같은 revision의 같은 내용은 멱등이고 다른 내용은 409이다`() {
        assign(SESSION, revision = 5).andExpect(status().isNoContent)
        assign(SESSION, revision = 5).andExpect(status().isNoContent)
        // revision은 회차마다 비교하므로 다른 회차는 따로 기록된다(#160)
        assign(OTHER_SESSION, revision = 5).andExpect(status().isNoContent)
        mockMvc
            .perform(delete("$PATH?sessionId=$SESSION&revision=5").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isConflict)
        cancel(revision = 5).andExpect(status().isConflict)
        stored() shouldBe ("ACTIVE" to 5L)

        cancel(revision = 6).andExpect(status().isNoContent)
        cancel(revision = 6).andExpect(status().isNoContent)
        assign(SESSION, revision = 6).andExpect(status().isConflict)
        stored() shouldBe ("CANCELLED" to 6L)
    }

    @Test
    fun `revision이 있는 취소는 확정 기록이 없어도 남겨 뒤늦은 확정이 되살리지 못한다`() {
        cancel(revision = 2).andExpect(status().isNoContent)
        stored() shouldBe ("CANCELLED" to 2L)

        assign(SESSION, revision = 1).andExpect(status().isNoContent)
        stored() shouldBe ("CANCELLED" to 2L)

        // 더 새로운 확정은 취소 기록을 되살린다
        assign(SESSION, revision = 3).andExpect(status().isNoContent)
        stored() shouldBe ("ACTIVE" to 3L)
    }

    @Test
    fun `revision 없이 보내는 이전 방식은 그대로 마지막 요청이 이긴다`() {
        assign(SESSION, revision = 4).andExpect(status().isNoContent)
        cancel().andExpect(status().isNoContent)
        stored() shouldBe ("CANCELLED" to 4L)

        // 다른 회차를 기록해도 취소한 회차는 그대로 두고 새 회차를 더한다(#160)
        assign(OTHER_SESSION).andExpect(status().isNoContent)
        jdbcTemplate.queryForList(
            "SELECT session_id || ':' || status FROM tb_preregister_session ORDER BY session_id",
            String::class.java,
        ) shouldBe listOf("$SESSION:CANCELLED", "$OTHER_SESSION:ACTIVE")
    }

    @Test
    fun `revision은 양수여야 하고 삭제된 박람회에는 취소 기록을 만들지 않는다`() {
        mockMvc
            .perform(
                put(PATH)
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionId":$SESSION,"revision":0}"""),
            ).andExpect(status().isBadRequest)
        cancel(revision = 0).andExpect(status().isBadRequest)

        deleteExpoDataService.delete(EXPO)
        cancel(revision = 1).andExpect(status().isNoContent)
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

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfig {
        @Bean
        @Primary
        fun testClock(): Clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    }

    private fun sessionAt(
        start: Instant,
        end: Instant,
        sessionId: Long = SESSION,
    ) {
        doReturn(PreregisterSessionResDto(sessionId, start, end)).`when`(expoClient).getPreregisterSession(EXPO, sessionId)
    }

    private fun scan(code: String = CODE): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","participantId":$PARTICIPANT,"code":"$code"}"""),
        )

    private fun assign(
        sessionId: Long,
        revision: Long? = null,
    ): ResultActions {
        val body = if (revision == null) """{"sessionId":$sessionId}""" else """{"sessionId":$sessionId,"revision":$revision}"""
        return mockMvc.perform(put(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
    }

    private fun cancel(revision: Long? = null): ResultActions =
        mockMvc.perform(delete(if (revision == null) PATH else "$PATH?revision=$revision").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun stored(): Pair<String, Long>? =
        jdbcTemplate
            .query(
                "SELECT status, revision FROM tb_preregister_session WHERE expo_id = ? AND participant_id = ? ORDER BY session_id NULLS LAST",
                { rs, _ -> rs.getString("status") to rs.getLong("revision") },
                EXPO,
                PARTICIPANT,
            ).firstOrNull()

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
        const val WRONG_CODE = "wrongWrongWrongWrongWr"
        const val SESSION = 11L
        const val OTHER_SESSION = 12L
        const val PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session"
    }
}
