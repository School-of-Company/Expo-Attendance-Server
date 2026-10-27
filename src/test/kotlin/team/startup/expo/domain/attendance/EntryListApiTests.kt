package team.startup.expo.domain.attendance

import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.global.client.user.EntryCardsReqDto
import team.startup.expo.global.client.user.EntryCardsResDto
import team.startup.expo.global.client.user.StandardParticipantEntryCardResDto
import team.startup.expo.global.client.user.TraineeEntryCardResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** 입구 화면의 하루 입장자 목록(#237). */
class EntryListApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var clock: Clock

    @MockitoBean
    private lateinit var userClient: UserClient

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_entry_log RESTART IDENTITY CASCADE")
        reset(userClient)
    }

    @Test
    fun `등록 QR 입장자를 최신순 한 행씩 주고 이름·가린 번호·명찰을 채운다`() {
        entry("STANDARD", "11", 9, 0)
        entry("TRAINEE", "21", 9, 30, exitedHour = 10)
        entry("STANDARD", "12", 10, 5)
        entry("PAPER_QR", "paper-token-1", 10, 10)
        entry("STANDARD", "13", 8, 0, date = today.minusDays(1))
        doReturn(
            EntryCardsResDto(
                standardParticipants =
                    listOf(
                        StandardParticipantEntryCardResDto(12, "교사", "010-1234-5678", true, "TEACHER", "빛고을고"),
                        StandardParticipantEntryCardResDto(11, "동행", null, false, "STUDENT", null),
                    ),
                trainees = listOf(TraineeEntryCardResDto(21, "연수", "01099998888", true, "빛고을초")),
            ),
        ).`when`(userClient).getEntryCards(EntryCardsReqDto(EXPO, listOf(12L, 11L), listOf(21L)))

        val body =
            entries()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].id").value(12))
                .andExpect(jsonPath("$.entries[0].participationType").value("STANDARD"))
                .andExpect(jsonPath("$.entries[0].name").value("교사"))
                .andExpect(jsonPath("$.entries[0].phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.entries[0].personalInformationStatus").value(true))
                .andExpect(jsonPath("$.entries[0].badge.name").value("교사"))
                .andExpect(jsonPath("$.entries[0].badge.school").value("빛고을고"))
                .andExpect(jsonPath("$.entries[0].firstEnteredTime").value("10:05"))
                .andExpect(jsonPath("$.entries[0].inside").value(true))
                .andExpect(jsonPath("$.entries[1].id").value(21))
                .andExpect(jsonPath("$.entries[1].participationType").value("TRAINEE"))
                .andExpect(jsonPath("$.entries[1].phoneNumber").value("010-****-8888"))
                .andExpect(jsonPath("$.entries[1].badge.school").value("빛고을초"))
                .andExpect(jsonPath("$.entries[1].inside").value(false))
                .andExpect(jsonPath("$.entries[2].id").value(11))
                .andExpect(jsonPath("$.entries[2].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.entries[2].badge").doesNotExist())
                .andExpect(jsonPath("$.entries[2].personalInformationStatus").value(false))
                .andReturn()
                .response.contentAsString
        body shouldNotContain "paper-token-1"
        body shouldNotContain "1234"
    }

    @Test
    fun `유저 서비스에서 지워진 참가자는 ID·유형·입장 시각만 담는다`() {
        entry("STANDARD", "11", 9, 0)
        doReturn(
            EntryCardsResDto(emptyList(), emptyList()),
        ).`when`(userClient).getEntryCards(EntryCardsReqDto(EXPO, listOf(11L), emptyList()))

        entries()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries[0].id").value(11))
            .andExpect(jsonPath("$.entries[0].name").doesNotExist())
            .andExpect(jsonPath("$.entries[0].badge").doesNotExist())
            .andExpect(jsonPath("$.entries[0].firstEnteredTime").value("09:00"))
    }

    @Test
    fun `page와 size로 나눠 읽고 날짜를 고를 수 있다`() {
        (0..4).forEach { entry("STANDARD", "${100 + it}", 9, it) }
        doReturn(EntryCardsResDto(emptyList(), emptyList()))
            .`when`(userClient)
            .getEntryCards(EntryCardsReqDto(EXPO, listOf(102L, 101L), emptyList()))

        entries("?size=2&page=1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.entries.length()").value(2))
            .andExpect(jsonPath("$.entries[0].id").value(102))
            .andExpect(jsonPath("$.entries[1].id").value(101))
        entries("?date=${today.minusDays(1)}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.entries.length()").value(0))
        entries("?size=0").andExpect(status().isBadRequest)
        entries("?size=501").andExpect(status().isBadRequest)
        entries("?page=-1").andExpect(status().isBadRequest)
    }

    @Test
    fun `입장자가 없으면 유저 서비스를 부르지 않고 빈 목록이다`() {
        entries().andExpect(status().isOk).andExpect(jsonPath("$.entries.length()").value(0))
        verifyNoInteractions(userClient)
    }

    @Test
    fun `유저 서비스가 응답하지 못하면 503이다`() {
        entry("STANDARD", "11", 9, 0)
        doThrow(RuntimeException("down")).`when`(userClient).getEntryCards(EntryCardsReqDto(EXPO, listOf(11L), emptyList()))

        entries().andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `관리자만 쓸 수 있다`() {
        mockMvc.perform(get("/attendance/summary/$EXPO/entries")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/attendance/summary/$EXPO/entries").header("X-User-Id", "2").header("X-User-Role", "ROLE_USER"))
            .andExpect(status().isForbidden)
    }

    private fun entries(query: String = ""): ResultActions =
        mockMvc.perform(get("/attendance/summary/$EXPO/entries$query").header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN"))

    private fun entry(
        type: String,
        subjectId: String,
        hour: Int,
        minute: Int,
        exitedHour: Int? = null,
        date: LocalDate = today,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, exited_at, category) VALUES (?, ?, ?, ?, ?, ?, ?)",
            EXPO,
            type,
            subjectId,
            date,
            date.atTime(hour, minute),
            exitedHour?.let { date.atTime(it, 0) as LocalDateTime },
            if (type == "PAPER_QR") "ADULT" else null,
        )
    }

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a12"
    }
}
