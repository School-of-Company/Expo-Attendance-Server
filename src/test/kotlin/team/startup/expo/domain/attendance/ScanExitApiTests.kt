package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ScanExitApiTests : IntegrationTestSupport() {
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

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_entry_log, tb_entry_event, tb_qr_entry, tb_qr_token, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        val today = LocalDate.now(clock)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        jdbcTemplate.update(
            "INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES (?, ?, 'ADULT', now())",
            TOKEN,
            EXPO,
        )
    }

    @Test
    fun `종이 QR은 입장한 뒤에 퇴장할 수 있고 두 번째 퇴장은 400이다`() {
        paperEntry().andExpect(status().isOk)

        exit("""{"token":"$TOKEN"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subjectType").value("PAPER_QR"))
            .andExpect(jsonPath("$.enteredTime").exists())
            .andExpect(jsonPath("$.exitedTime").exists())
            .andExpect(jsonPath("$.stayedMinutes").value(0))

        exit("""{"token":"$TOKEN"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("오늘 이미 퇴장 처리된 QR입니다."))
        // 입장 로그에는 종이 QR의 구분이 남는다
        jdbcTemplate.queryForObject("SELECT category FROM tb_entry_log", String::class.java) shouldBe "ADULT"
    }

    @Test
    fun `입장하지 않았거나 없는 종이 QR의 퇴장은 거절한다`() {
        exit("""{"token":"$TOKEN"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("오늘 입장 기록이 없습니다."))
        exit("""{"token":"unknown-token"}""").andExpect(status().isNotFound)
        // 다른 박람회의 토큰은 이 박람회에서 찾을 수 없다
        jdbcTemplate.update(
            "INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES ('other-token', 'other-expo', 'CHILD', now())",
        )
        exit("""{"token":"other-token"}""").andExpect(status().isNotFound)
    }

    @Test
    fun `일반 참가자는 코드를 확인하고 입장한 뒤에만 퇴장한다`() {
        doReturn(
            entry(PARTICIPANT, "STANDARD"),
        ).`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))

        // 입장 전 퇴장은 400
        exit("""{"participantId":$PARTICIPANT,"code":"$CODE"}""").andExpect(status().isBadRequest)

        mockMvc
            .perform(
                patch("/attendance/$EXPO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":$PARTICIPANT,"code":"$CODE"}"""),
            ).andExpect(status().isOk)

        exit("""{"participantId":$PARTICIPANT,"code":"$CODE"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subjectType").value("STANDARD"))
        exit("""{"participantId":$PARTICIPANT,"code":"$CODE"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `틀린 코드나 모르는 참가자는 404이고 유저 장애는 503이다`() {
        doThrow(
            feignException(404),
        ).`when`(userClient).verifyStandardParticipant(VerifyStandardParticipantReqDto(EXPO, PARTICIPANT, "wrongWrongWrongWrongWr"))
        exit("""{"participantId":$PARTICIPANT,"code":"wrongWrongWrongWrongWr"}""").andExpect(status().isNotFound)

        doThrow(feignException(500)).`when`(userClient).verifyStandardParticipant(VerifyStandardParticipantReqDto(EXPO, PARTICIPANT, CODE))
        exit("""{"participantId":$PARTICIPANT,"code":"$CODE"}""").andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `연수자는 입장한 뒤 traineeId로 퇴장한다`() {
        doReturn(entry(TRAINEE, "TRAINEE")).`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "TRAINEE", phoneNumber = "01011112222"))
        doReturn(listOf(TraineeNameResDto(TRAINEE, "김연수"))).`when`(userClient).getTraineeNames(TraineeNamesReqDto(EXPO, listOf(TRAINEE)))

        mockMvc
            .perform(
                patch("/attendance/$EXPO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_TRAINEE","phoneNumber":"01011112222"}"""),
            ).andExpect(status().isOk)

        exit("""{"traineeId":$TRAINEE}""").andExpect(status().isOk).andExpect(jsonPath("$.subjectType").value("TRAINEE"))
        exit("""{"traineeId":$TRAINEE}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `요청은 세 가지 중 하나만 받고 입력을 검증한다`() {
        listOf(
            """{}""",
            """{"participantId":$PARTICIPANT}""",
            """{"code":"$CODE"}""",
            """{"token":"$TOKEN","traineeId":$TRAINEE}""",
            """{"participantId":$PARTICIPANT,"code":"$CODE","token":"$TOKEN"}""",
            """{"participantId":0,"code":"$CODE"}""",
            """{"code":"${"c".repeat(23)}","participantId":$PARTICIPANT}""",
        ).forEach { exit(it).andExpect(status().isBadRequest) }
    }

    @Test
    fun `같은 날 두 번째 입장은 입장 로그를 늘리지 않고 박람회를 삭제하면 로그도 지운다`() {
        paperEntry().andExpect(status().isOk)
        paperEntry().andExpect(status().isBadRequest)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_log", Long::class.java) shouldBe 1L

        deleteExpoDataService.delete(EXPO)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_log", Long::class.java) shouldBe 0L
    }

    @Test
    fun `같은 종이 QR을 동시에 퇴장 스캔해도 한 번만 퇴장 처리된다`() {
        paperEntry().andExpect(status().isOk)
        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val statuses =
            List(threads) {
                executor.submit(
                    Callable {
                        start.await()
                        exit("""{"token":"$TOKEN"}""").andReturn().response.status
                    },
                )
            }
        start.countDown()
        val results = statuses.map { it.get() }
        executor.shutdown()

        results.count { it == 200 } shouldBe 1
        results.count { it == 400 } shouldBe threads - 1
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_log WHERE exited_at IS NOT NULL", Long::class.java) shouldBe 1L
    }

    private fun paperEntry(): ResultActions =
        mockMvc.perform(patch("/attendance/qr/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$TOKEN"}"""))

    private fun exit(body: String): ResultActions =
        mockMvc.perform(patch("/attendance/exit/$EXPO").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun entry(
        id: Long,
        type: String,
    ) = RecordEntryResDto(id, "홍길동", "01012345678", true, type, if (type == "STANDARD") "STUDENT" else null, "한빛중")

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
        const val TOKEN = "paper-token-0001"
        const val PARTICIPANT = 7001L
        const val TRAINEE = 9001L
        const val CODE = "codePPPPPPPPPPPPPPPPPP"
    }
}
