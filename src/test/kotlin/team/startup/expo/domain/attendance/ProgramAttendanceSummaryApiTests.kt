package team.startup.expo.domain.attendance

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
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalTime

class ProgramAttendanceSummaryApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var standardRepository: StandardProgramAttendanceRepository

    @Autowired
    private lateinit var trainingRepository: TrainingProgramAttendanceRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_standard_program_attendance, tb_training_program_attendance RESTART IDENTITY CASCADE")
    }

    @Test
    fun `프로그램별 출석 인원과 입실 시각 범위를 요청 순서대로 돌려주고 출석이 없으면 0이다`() {
        val day = LocalDate.of(2026, 10, 20)
        standardRepository.insertEntryIfAbsent(4001, 1, day, LocalTime.of(9, 5))
        standardRepository.insertEntryIfAbsent(4001, 2, day, LocalTime.of(10, 30))
        standardRepository.insertEntryIfAbsent(4001, 2, day, LocalTime.of(11, 0))
        // 같은 ID의 연수 프로그램은 다른 프로그램이다
        trainingRepository.insertEntryIfAbsent(4001, 9, day, LocalTime.of(14, 0))

        summarize(
            """{"programs":[
                {"programType":"TRAINING","programId":4001},
                {"programType":"STANDARD","programId":4001},
                {"programType":"STANDARD","programId":4002},
                {"programType":"STANDARD","programId":4001}
            ]}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].programType").value("TRAINING"))
            .andExpect(jsonPath("$[0].attendeeCount").value(1))
            .andExpect(jsonPath("$[0].firstEntryTime").value("14:00"))
            .andExpect(jsonPath("$[1].programType").value("STANDARD"))
            .andExpect(jsonPath("$[1].attendeeCount").value(2))
            .andExpect(jsonPath("$[1].firstEntryTime").value("09:05"))
            .andExpect(jsonPath("$[1].lastEntryTime").value("10:30"))
            .andExpect(jsonPath("$[2].programId").value(4002))
            .andExpect(jsonPath("$[2].attendeeCount").value(0))
            .andExpect(jsonPath("$[2].firstEntryTime").doesNotExist())
    }

    @Test
    fun `내부 토큰이 필요하고 빈 목록과 500개 초과와 잘못된 항목은 400이다`() {
        mockMvc
            .perform(
                post(PATH).contentType(MediaType.APPLICATION_JSON).content("""{"programs":[{"programType":"STANDARD","programId":1}]}"""),
            ).andExpect(status().isUnauthorized)

        val tooMany = (1..501).joinToString(",") { """{"programType":"STANDARD","programId":$it}""" }
        listOf(
            """{"programs":[]}""",
            """{}""",
            """{"programs":[$tooMany]}""",
            """{"programs":[{"programType":"OTHER","programId":1}]}""",
            """{"programs":[{"programType":"STANDARD","programId":0}]}""",
        ).forEach { summarize(it).andExpect(status().isBadRequest) }
    }

    private fun summarize(body: String): ResultActions =
        mockMvc.perform(post(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private companion object {
        const val PATH = "/internal/program-attendances/summary"
    }
}
