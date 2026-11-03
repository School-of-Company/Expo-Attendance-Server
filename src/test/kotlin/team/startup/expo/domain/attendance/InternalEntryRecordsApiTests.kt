package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
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
import team.startup.expo.domain.attendance.support.EntryRecordCursor
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** #150: Report용 입장 기록 내부 API. */
class InternalEntryRecordsApiTests : IntegrationTestSupport() {
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
            "TRUNCATE TABLE tb_entry_log, tb_entry_event, tb_qr_entry, tb_qr_token, tb_walk_in_log, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
    }

    @Test
    fun `종이 QR의 입장 퇴장 재입장이 시간순으로 나오고 구분과 코드는 있고 토큰은 없다`() {
        jdbcTemplate.update(
            "INSERT INTO tb_qr_token (token, expo_id, category, created_at, serial, code) VALUES (?, ?, 'ADULT', now(), 1, 'ADT-0001-K7')",
            TOKEN,
            EXPO,
        )
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)
        entry().andExpect(status().isOk)

        val body =
            records("")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.records.length()").value(3))
                .andExpect(jsonPath("$.records[0].eventType").value("ENTER"))
                .andExpect(jsonPath("$.records[1].eventType").value("EXIT"))
                .andExpect(jsonPath("$.records[2].eventType").value("RE_ENTER"))
                .andExpect(jsonPath("$.records[0].subjectType").value("PAPER_QR"))
                .andExpect(jsonPath("$.records[0].subjectId").doesNotExist())
                .andExpect(jsonPath("$.records[0].category").value("ADULT"))
                .andExpect(jsonPath("$.records[1].category").value("ADULT"))
                .andExpect(jsonPath("$.records[2].paperCode").value("ADT-0001-K7"))
                .andReturn()
                .response
                .contentAsString
        body shouldNotContain TOKEN
    }

    @Test
    fun `등록 QR의 입장 퇴장 재입장도 시간순으로 나오고 대상 ID는 숫자다`() {
        event("STANDARD", "7001", today, "ENTER", "10:00", "gate-1")
        event("STANDARD", "7001", today, "EXIT", "11:00", null)
        event("STANDARD", "7001", today, "RE_ENTER", "12:00", null)
        event("TRAINEE", "88", today, "ENTER", "10:30", null)

        records("")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.records[0].eventType").value("ENTER"))
            .andExpect(jsonPath("$.records[0].subjectType").value("STANDARD"))
            .andExpect(jsonPath("$.records[0].subjectId").value(7001))
            .andExpect(jsonPath("$.records[0].scanner").value("gate-1"))
            .andExpect(jsonPath("$.records[0].category").doesNotExist())
            .andExpect(jsonPath("$.records[0].paperCode").doesNotExist())
            .andExpect(jsonPath("$.records[1].subjectType").value("TRAINEE"))
            .andExpect(jsonPath("$.records[1].subjectId").value(88))
            .andExpect(jsonPath("$.records[2].eventType").value("EXIT"))
            .andExpect(jsonPath("$.records[3].eventType").value("RE_ENTER"))
    }

    @Test
    fun `여러 페이지로 나눠도 겹침과 누락이 없고 totalElements는 같다`() {
        // 같은 시각 이벤트는 id 순이다
        repeat(7) { event("STANDARD", (100 + it).toString(), today, "ENTER", "10:00", null) }

        val seen = mutableListOf<Int>()
        (0..2).forEach { page ->
            val json =
                records("page=$page&size=3")
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalElements").value(7))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(3))
                    .andReturn()
                    .response
                    .contentAsString
            Regex("\"subjectId\":(\\d+)").findAll(json).forEach { seen += it.groupValues[1].toInt() }
        }
        seen shouldBe (100..106).toList()
        records("page=2&size=3").andExpect(jsonPath("$.records.length()").value(1))
        records("page=3&size=3").andExpect(jsonPath("$.records.length()").value(0)).andExpect(jsonPath("$.totalElements").value(7))
    }

    @Test
    fun `cursor로 끝까지 이어 읽으면 겹침과 누락이 없고 마지막 페이지에는 nextCursor가 없다(#250)`() {
        // 같은 시각 이벤트는 id 순이다
        repeat(7) { event("STANDARD", (100 + it).toString(), today, "ENTER", "10:00", null) }
        walkIn(today, "ADULT", 1)

        val first =
            json(
                records("size=3")
                    .andExpect(jsonPath("$.totalElements").value(7))
                    .andExpect(jsonPath("$.walkIn.length()").value(1)),
            )
        val seen = subjectIds(first).toMutableList()
        var cursor: String? = nextCursor(first)
        var requests = 1
        while (cursor != null) {
            val next =
                json(
                    records("size=3&cursor=$cursor")
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").doesNotExist())
                        .andExpect(jsonPath("$.walkIn.length()").value(0)),
                )
            seen += subjectIds(next)
            cursor = nextCursor(next)
            requests++
        }
        seen shouldBe (100..106).toList()
        // 3 + 3 + 1건. 마지막 페이지를 안 뒤에 빈 페이지를 한 번 더 부르지 않는다
        requests shouldBe 3
    }

    @Test
    fun `건수가 페이지 크기의 배수여도 마지막 페이지에는 nextCursor가 없다`() {
        repeat(6) { event("STANDARD", (100 + it).toString(), today, "ENTER", "10:00", null) }

        val cursor = requireNotNull(nextCursor(json(records("size=3"))))
        records("size=3&cursor=$cursor")
            .andExpect(jsonPath("$.records.length()").value(3))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `OFFSET 페이지의 nextCursor로 이어 읽어도 같은 순서다`() {
        repeat(7) { event("STANDARD", (100 + it).toString(), today, "ENTER", "10:00", null) }

        val second = json(records("page=1&size=3"))
        subjectIds(second) shouldBe listOf(103, 104, 105)
        val cursor = requireNotNull(nextCursor(second))
        subjectIds(json(records("size=3&cursor=$cursor"))) shouldBe listOf(106)
    }

    @Test
    fun `cursor는 날짜를 넘어 이어 읽고 범위 밖 날짜는 읽지 않는다`() {
        val d = LocalDate.of(2026, 10, 19)
        event("STANDARD", "1", d.minusDays(1), "ENTER", "23:00", null)
        event("STANDARD", "2", d, "ENTER", "18:00", null)
        event("STANDARD", "3", d, "EXIT", "19:00", null)
        event("STANDARD", "4", d.plusDays(1), "ENTER", "09:00", null)
        event("STANDARD", "5", d.plusDays(2), "ENTER", "09:00", null)

        val range = "from=$d&to=${d.plusDays(1)}"
        val seen = mutableListOf<Int>()
        var cursor: String? = null
        do {
            val page = json(records("$range&size=1" + (cursor?.let { "&cursor=$it" } ?: "")))
            seen += subjectIds(page)
            cursor = nextCursor(page)
        } while (cursor != null)
        seen shouldBe listOf(2, 3, 4)
    }

    @Test
    fun `날짜 범위는 양 끝을 포함하고 범위 밖은 빠진다`() {
        val d = LocalDate.of(2026, 10, 19)
        event("STANDARD", "1", d.minusDays(1), "ENTER", "10:00", null)
        event("STANDARD", "2", d, "ENTER", "10:00", null)
        event("STANDARD", "3", d.plusDays(1), "ENTER", "10:00", null)
        event("STANDARD", "4", d.plusDays(2), "ENTER", "10:00", null)

        records("from=$d&to=${d.plusDays(1)}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value(d.toString()))
            .andExpect(jsonPath("$.to").value(d.plusDays(1).toString()))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.records[0].subjectId").value(2))
            .andExpect(jsonPath("$.records[1].subjectId").value(3))
        records("").andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.from").doesNotExist())
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        listOf(
            "from=2026-10-19",
            "to=2026-10-19",
            "from=2026-10-20&to=2026-10-19",
            "size=5001",
            "size=0",
            "page=-1",
            "from=abc&to=2026-10-19",
            "cursor=not-a-cursor",
            "cursor=${java.util.Base64.getUrlEncoder().encodeToString("2026-10-19|x|1".toByteArray())}",
            "page=1&cursor=${EntryRecordCursor(today, today.atTime(10, 0), 1).encode()}",
        ).forEach { records(it).andExpect(status().isBadRequest) }
        records("size=5000").andExpect(status().isOk)
        mockMvc
            .perform(get("/internal/expos/not-a-uuid/entry-records").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `간편입장은 첫 페이지에만 날짜와 구분별 순증감으로 나오고 합계 0은 빠진다`() {
        event("STANDARD", "1", today, "ENTER", "10:00", null)
        event("STANDARD", "2", today, "ENTER", "10:01", null)
        walkIn(today, "TEACHER", 1)
        walkIn(today, "TEACHER", 1)
        walkIn(today, "TEACHER", -1)
        walkIn(today, "ADULT", 1)
        walkIn(today, "GENERAL", 1)
        walkIn(today, "GENERAL", -1)
        walkIn(today.plusDays(1), "ADULT", 1)
        walkIn(today, "ADULT", 1, OTHER_EXPO)

        records("page=0&size=1")
            .andExpect(jsonPath("$.walkIn.length()").value(3))
            .andExpect(jsonPath("$.walkIn[0].date").value(today.toString()))
            .andExpect(jsonPath("$.walkIn[0].category").value("ADULT"))
            .andExpect(jsonPath("$.walkIn[0].count").value(1))
            .andExpect(jsonPath("$.walkIn[1].category").value("TEACHER"))
            .andExpect(jsonPath("$.walkIn[1].count").value(1))
            .andExpect(jsonPath("$.walkIn[2].date").value(today.plusDays(1).toString()))
        records("page=1&size=1").andExpect(jsonPath("$.walkIn.length()").value(0)).andExpect(jsonPath("$.records.length()").value(1))
        // 날짜 범위로 좁히면 간편입장도 그 범위만 합친다
        records("from=$today&to=$today").andExpect(jsonPath("$.walkIn.length()").value(2))
    }

    @Test
    fun `다른 박람회의 기록은 섞이지 않는다`() {
        event("STANDARD", "1", today, "ENTER", "10:00", null)
        event("STANDARD", "2", today, "ENTER", "10:00", null, OTHER_EXPO)

        records("").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.records[0].subjectId").value(1))
    }

    @Test
    fun `기록이 없는 박람회는 빈 배열과 0이다`() {
        records("")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expoId").value(EXPO))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.records.length()").value(0))
            .andExpect(jsonPath("$.walkIn.length()").value(0))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `내부 토큰이 없거나 틀리면 401이다`() {
        mockMvc.perform(get("/internal/expos/$EXPO/entry-records")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/internal/expos/$EXPO/entry-records").header("X-Internal-Token", "wrong-token"))
            .andExpect(status().isUnauthorized)
    }

    private fun json(result: ResultActions): String =
        result
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString

    private fun subjectIds(json: String): List<Int> =
        Regex("\"subjectId\":(\\d+)")
            .findAll(json)
            .map { it.groupValues[1].toInt() }
            .toList()

    private fun nextCursor(json: String): String? = Regex("\"nextCursor\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    private fun records(query: String): ResultActions =
        mockMvc.perform(get("/internal/expos/$EXPO/entry-records?$query").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun event(
        type: String,
        subjectId: String,
        date: LocalDate,
        eventType: String,
        time: String,
        scanner: String?,
        expo: String = EXPO,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner) VALUES (?, ?, ?, ?, ?, ?, ?)",
            expo,
            type,
            subjectId,
            java.sql.Date.valueOf(date),
            eventType,
            Timestamp.valueOf(LocalDateTime.of(date, java.time.LocalTime.parse(time))),
            scanner,
        )
    }

    private fun walkIn(
        date: LocalDate,
        category: String,
        delta: Int,
        expo: String = EXPO,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_walk_in_log (expo_id, attendance_date, category, delta, created_at) VALUES (?, ?, ?, ?, now())",
            expo,
            java.sql.Date.valueOf(date),
            category,
            delta,
        )
    }

    private fun entry(): ResultActions =
        mockMvc.perform(patch("/attendance/qr/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$TOKEN"}"""))

    private fun exit(): ResultActions =
        mockMvc.perform(patch("/attendance/exit/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$TOKEN"}"""))

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10"
        const val OTHER_EXPO = "1c2a7d4f-63b5-4e7f-8e68-5c8b2f6dab21"
        const val TOKEN = "paper-token-0150"
    }
}
