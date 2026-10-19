package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.ResolveParticipantResDto
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantResDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** #148: 등록 QR(참가자·연수자)도 퇴장한 뒤 다시 입장 스캔하면 재입장이다. 유저 서비스는 오늘 입장했다는 409만 준다. */
class RegisteredQrReentryApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var clock: Clock

    @MockitoBean
    private lateinit var userClient: UserClient

    @MockitoBean
    private lateinit var expoClient: ExpoClient

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_entry_log, tb_entry_event, tb_cancellation_guard, tb_preregister_session, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        // 유저 서비스는 처음에는 입장을 기록하고 이후에는 오늘 이미 입장했다고(409) 답한다
        org.mockito.Mockito
            .doReturn(RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "TEACHER", "한빛중"))
            .doThrow(feign(409))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
        org.mockito.Mockito
            .doReturn(RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "TEACHER", "한빛중"))
            .doThrow(feign(409))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", phoneNumber = PHONE))
        doReturn(ResolveStandardParticipantResDto(PARTICIPANT))
            .`when`(userClient)
            .resolveStandardParticipant(ResolveStandardParticipantReqDto(EXPO, PHONE))
        doReturn(listOf(StandardParticipantNameResDto(PARTICIPANT, "홍길동")))
            .`when`(userClient)
            .getStandardParticipantNames(StandardParticipantNamesReqDto(EXPO, listOf(PARTICIPANT)))
        org.mockito.Mockito
            .doReturn(RecordEntryResDto(TRAINEE, "김연수", PHONE, true, "TRAINEE", null, "한빛중"))
            .doThrow(feign(409))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "TRAINEE", phoneNumber = PHONE))
        doReturn(ResolveParticipantResDto(TRAINEE, "TRAINEE"))
            .`when`(userClient)
            .resolveParticipant(ResolveParticipantReqDto(EXPO, PHONE, "TRAINEE"))
        doReturn(listOf(TraineeNameResDto(TRAINEE, "김연수")))
            .`when`(userClient)
            .getTraineeNames(TraineeNamesReqDto(EXPO, listOf(TRAINEE)))
    }

    @Test
    fun `처음 입장은 FIRST와 최초 입장 시각을 응답하고 기존 필드는 그대로다`() {
        standardEntry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("FIRST"))
            .andExpect(jsonPath("$.firstEnteredTime").exists())
            .andExpect(jsonPath("$.id").value(PARTICIPANT))
            .andExpect(jsonPath("$.personalInformationStatus").value(true))
            .andExpect(jsonPath("$.badge.school").value("한빛중"))
    }

    @Test
    fun `퇴장 없이 다시 찍으면 400이고 퇴장한 뒤 다시 입장하면 재입장이며 반복할 수 있다`() {
        standardEntry().andExpect(status().isOk)
        standardEntry().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("이미 박람회에 입장한 유저입니다."))

        standardExit().andExpect(status().isOk)
        standardEntry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))
            .andExpect(jsonPath("$.firstEnteredTime").exists())
            .andExpect(jsonPath("$.id").value(PARTICIPANT))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.participationType").value("STANDARD"))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.personalInformationStatus").doesNotExist())
            .andExpect(jsonPath("$.badge").doesNotExist())
        standardEntry().andExpect(status().isBadRequest)
        standardExit().andExpect(status().isOk)

        events() shouldBe listOf("ENTER", "EXIT", "RE_ENTER", "EXIT")
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_log", Long::class.java) shouldBe 1L
    }

    @Test
    fun `전화번호로 입장한 참가자도 재입장하고 스캔한 번호를 돌려준다`() {
        phoneEntry().andExpect(status().isOk)
        jdbcTemplate.update("UPDATE tb_entry_log SET exited_at = now()")
        phoneEntry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))
            .andExpect(jsonPath("$.phoneNumber").value(PHONE))
    }

    @Test
    fun `연수자는 전화번호로 ID를 찾아 재입장한다`() {
        traineeEntry().andExpect(status().isOk).andExpect(jsonPath("$.entryStatus").value("FIRST"))
        traineeEntry().andExpect(status().isBadRequest)

        mockMvc
            .perform(
                patch("/attendance/exit/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"traineeId":$TRAINEE}"""),
            ).andExpect(status().isOk)
        traineeEntry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))
            .andExpect(jsonPath("$.id").value(TRAINEE))
            .andExpect(jsonPath("$.name").value("김연수"))
            .andExpect(jsonPath("$.participationType").value("TRAINEE"))
        events() shouldBe listOf("ENTER", "EXIT", "RE_ENTER")
    }

    @Test
    fun `재입장은 입장 인원을 늘리지 않고 재입장 건수와 지금 안에 있는 인원에 반영된다`() {
        standardEntry().andExpect(status().isOk)
        standardExit().andExpect(status().isOk)
        summary()
            .andExpect(jsonPath("$.totals.entered").value(1))
            .andExpect(jsonPath("$.totals.stillInside").value(0))
            .andExpect(jsonPath("$.reentered").value(0))

        standardEntry().andExpect(status().isOk)
        summary()
            .andExpect(jsonPath("$.totals.entered").value(1))
            .andExpect(jsonPath("$.totals.exited").value(0))
            .andExpect(jsonPath("$.totals.stillInside").value(1))
            .andExpect(jsonPath("$.uniqueQr").value(1))
            .andExpect(jsonPath("$.reentered").value(1))
        mockMvc
            .perform(get("/attendance/summary/$EXPO?all=true").header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN"))
            .andExpect(jsonPath("$.reentered").value(1))
    }

    @Test
    fun `입장 로그가 없는 입장자의 재스캔은 재입장이 아니라 400이다`() {
        standardEntry().andExpect(status().isOk)
        // 입장 로그 도입 전 입장이나 로그 유실을 흉내 낸다
        jdbcTemplate.update("DELETE FROM tb_entry_log")
        standardEntry().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("이미 박람회에 입장한 유저입니다."))
        events() shouldBe listOf("ENTER")
    }

    @Test
    fun `취소 보호가 걸린 참가자는 재입장도 거절한다`() {
        standardEntry().andExpect(status().isOk)
        standardExit().andExpect(status().isOk)

        guard("HELD")
        standardEntry().andExpect(status().isConflict).andExpect(jsonPath("$.message").value("신청 취소를 처리 중입니다."))
        jdbcTemplate.update("UPDATE tb_cancellation_guard SET state = 'COMMITTED'")
        standardEntry().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))

        events() shouldBe listOf("ENTER", "EXIT")
        jdbcTemplate.update("DELETE FROM tb_cancellation_guard")
        standardEntry().andExpect(status().isOk).andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))
    }

    @Test
    fun `퇴장한 참가자를 동시에 찍어도 재입장은 한 번만 기록된다`() {
        standardEntry().andExpect(status().isOk)
        standardExit().andExpect(status().isOk)

        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val statuses =
            List(threads) {
                executor.submit(
                    Callable {
                        start.await()
                        standardEntry().andReturn().response.status
                    },
                )
            }
        start.countDown()
        val results = statuses.map { it.get() }
        executor.shutdown()

        results.count { it == 200 } shouldBe 1
        results.count { it == 400 } shouldBe threads - 1
        events().count { it == "RE_ENTER" } shouldBe 1
    }

    private fun guard(state: String) {
        jdbcTemplate.update(
            "INSERT INTO tb_cancellation_guard (expo_id, participant_id, operation_id, state, created_at, updated_at) " +
                "VALUES (?, ?, 'op-1', ?, now(), now())",
            EXPO,
            PARTICIPANT,
            state,
        )
    }

    private fun events(): List<String> =
        jdbcTemplate.queryForList("SELECT event_type FROM tb_entry_event ORDER BY id", String::class.java).filterNotNull()

    private fun standardEntry(): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","participantId":$PARTICIPANT,"code":"$CODE"}"""),
        )

    private fun phoneEntry(): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","phoneNumber":"$PHONE"}"""),
        )

    private fun traineeEntry(): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_TRAINEE","phoneNumber":"$PHONE"}"""),
        )

    private fun standardExit(): ResultActions =
        mockMvc.perform(
            patch("/attendance/exit/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"participantId":$PARTICIPANT,"code":"$CODE"}"""),
        )

    private fun summary(): ResultActions =
        mockMvc.perform(get("/attendance/summary/$EXPO?date=$today").header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN"))

    private fun feign(status: Int): FeignException =
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
        const val TRAINEE = 9001L
        const val CODE = "codePPPPPPPPPPPPPPPPPP"
        const val PHONE = "01012345678"
    }
}
