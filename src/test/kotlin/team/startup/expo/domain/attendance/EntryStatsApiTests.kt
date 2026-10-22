package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.FieldVisitFixtures
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** 실시간 입·퇴장 현황용 집계(#131): 시간대별, 최근 기록, 스캐너별, 최근 10분·고유 QR·전체 기간. */
class EntryStatsApiTests : IntegrationTestSupport() {
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
        FieldVisitFixtures.clear(jdbcTemplate)
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_walk_in_log, tb_entry_log, tb_entry_event, tb_qr_entry, tb_qr_token, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        doReturn(listOf(StandardParticipantNameResDto(7001, "홍길동")))
            .`when`(userClient)
            .getStandardParticipantNames(StandardParticipantNamesReqDto(EXPO, listOf(7001L)))

        // gate-1: 종이 QR 성인 2명 입장(한 명 퇴장) + 간편입장 1, gate-2: 종이 QR 아이 1명 + 등록 QR 1명 입장, 간편입장은 헤더 없이 1
        listOf("paper-a1" to "ADULT", "paper-a2" to "ADULT", "paper-c1" to "CHILD").forEach { (token, category) ->
            jdbcTemplate.update(
                "INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES (?, ?, ?, now())",
                token,
                EXPO,
                category,
            )
        }
        scanPaper("paper-a1", "gate-1")
        scanPaper("paper-a2", "gate-1")
        scanPaper("paper-c1", "gate-2")
        exitPaper("paper-a1", "gate-1")
        doReturn(RecordEntryResDto(7001, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null))
            .`when`(userClient)
            .recordEntry(
                FieldVisitFixtures.entry(
                    jdbcTemplate,
                    clock,
                    RecordEntryReqDto(EXPO, "STANDARD", participantId = 7001, code = "codePPPPPPPPPPPPPPPPPP"),
                ),
            )
        mockMvc
            .perform(
                patch("/attendance/$EXPO")
                    .header("X-Scanner-Name", "gate-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":7001,"code":"codePPPPPPPPPPPPPPPPPP"}"""),
            ).andExpect(status().isOk)
        walkIn("""{"category":"ADULT"}""", "gate-1")
        walkIn("""{"category":"STUDENT"}""", null)
    }

    @Test
    fun `스캐너별 입장·퇴장 처리 건수를 많은 순으로 돌려주고 헤더가 없는 처리는 세지 않는다`() {
        scanners()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scanners.length()").value(2))
            // gate-1: 종이 QR 입장 2 + 간편입장 1, 퇴장 1
            .andExpect(jsonPath("$.scanners[0].scanner").value("gate-1"))
            .andExpect(jsonPath("$.scanners[0].entered").value(3))
            .andExpect(jsonPath("$.scanners[0].exited").value(1))
            // gate-2: 종이 QR 입장 1 + 등록 QR 입장 1
            .andExpect(jsonPath("$.scanners[1].scanner").value("gate-2"))
            .andExpect(jsonPath("$.scanners[1].entered").value(2))
            .andExpect(jsonPath("$.scanners[1].exited").value(0))

        scanners("?limit=1").andExpect(jsonPath("$.scanners.length()").value(1))
        scanners("?limit=0").andExpect(status().isBadRequest)
        scanners("?limit=101").andExpect(status().isBadRequest)
    }

    @Test
    fun `스캐너 이름은 다듬어 저장한다`() {
        walkIn("""{"category":"ADULT"}""", "  gate-3  ")
        walkIn("""{"category":"ADULT"}""", "x".repeat(60))
        walkIn("""{"category":"ADULT"}""", "   ")

        jdbcTemplate.queryForList(
            "SELECT DISTINCT scanner FROM tb_walk_in_log WHERE scanner IS NOT NULL ORDER BY 1",
            String::class.java,
        ) shouldBe
            listOf("gate-1", "gate-3", "x".repeat(40))
    }

    @Test
    fun `최근 입·퇴장 기록은 최신순이고 방식·구분·스캐너·이름을 담되 종이 QR 토큰은 싣지 않는다`() {
        val body =
            recent("?limit=30")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.records.length()").value(7))
                .andReturn()
                .response.contentAsString
        body shouldNotContain "paper-a1"
        body shouldNotContain "paper-c1"

        // 가장 최근은 헤더 없이 센 간편입장(학생)
        recent()
            .andExpect(jsonPath("$.records[0].type").value("ENTER"))
            .andExpect(jsonPath("$.records[0].method").value("WALK_IN"))
            .andExpect(jsonPath("$.records[0].category").value("STUDENT"))
            .andExpect(jsonPath("$.records[0].scanner").doesNotExist())
            .andExpect(jsonPath("$.records[?(@.method=='REGISTERED_STANDARD')].name").value("홍길동"))
            .andExpect(jsonPath("$.records[?(@.method=='REGISTERED_STANDARD')].scanner").value("gate-2"))
            .andExpect(jsonPath("$.records[?(@.type=='EXIT')].method").value("PAPER_QR"))
            .andExpect(jsonPath("$.records[?(@.type=='EXIT')].scanner").value("gate-1"))
            .andExpect(jsonPath("$.records[?(@.method=='PAPER_QR' && @.category=='CHILD')].scanner").value("gate-2"))

        recent("?limit=3").andExpect(jsonPath("$.records.length()").value(3))
        recent("?limit=0").andExpect(status().isBadRequest)
        recent("?limit=101").andExpect(status().isBadRequest)
    }

    @Test
    fun `유저 서비스가 이름을 주지 못해도 최근 기록은 이름만 비우고 돌려준다`() {
        doThrow(
            RuntimeException("down"),
        ).`when`(userClient).getStandardParticipantNames(StandardParticipantNamesReqDto(EXPO, listOf(7001L)))

        val body =
            recent()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.records.length()").value(7))
                .andExpect(jsonPath("$.records[?(@.method=='REGISTERED_STANDARD')].type").value("ENTER"))
                .andReturn()
                .response.contentAsString
        body shouldNotContain "홍길동"
    }

    @Test
    fun `간편입장 취소는 최근 기록에 CANCEL로 남는다`() {
        walkIn("""{"category":"STUDENT","action":"UNDO"}""", "gate-1")

        recent("?limit=1")
            .andExpect(jsonPath("$.records[0].type").value("CANCEL"))
            .andExpect(jsonPath("$.records[0].method").value("WALK_IN"))
            .andExpect(jsonPath("$.records[0].scanner").value("gate-1"))
    }

    @Test
    fun `시간대별 입·퇴장 건수의 합은 오늘 입장 처리 건수와 같다`() {
        val buckets = hourlyBuckets()
        // 종이 QR 3 + 등록 QR 1 + 간편입장 2(퇴장은 입장에 세지 않는다)
        buckets.sumOf { it.second } shouldBe 6L
        buckets.sumOf { it.third } shouldBe 1L
    }

    @Test
    fun `요약은 최근 10분 입장 수와 서로 다른 QR 수를 담는다`() {
        summary()
            .andExpect(status().isOk)
            // QR 입장 4(종이 3 + 등록 1) + 간편입장 2
            .andExpect(jsonPath("$.recentEntered10m").value(6))
            // 간편입장은 QR이 아니다
            .andExpect(jsonPath("$.uniqueQr").value(4))
            .andExpect(jsonPath("$.date").value(today.toString()))
    }

    @Test
    fun `박람회 전체 기간 요약은 날짜 없이 모든 날을 모으고 고유 QR은 한 번만 센다`() {
        // 어제도 paper-a1이 입장했고 간편입장 3명을 셌다
        val yesterday = today.minusDays(1)
        jdbcTemplate.update(
            "INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, category) VALUES (?, 'PAPER_QR', 'paper-a1', ?, ?, 'ADULT')",
            EXPO,
            yesterday,
            LocalDateTime.of(yesterday, java.time.LocalTime.of(10, 5)),
        )
        repeat(3) {
            jdbcTemplate.update(
                "INSERT INTO tb_walk_in_log (expo_id, attendance_date, category, delta, created_at) VALUES (?, ?, 'ADULT', 1, ?)",
                EXPO,
                yesterday,
                LocalDateTime.of(yesterday, java.time.LocalTime.of(11, 0)),
            )
        }

        summary("?all=true")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.date").doesNotExist())
            .andExpect(jsonPath("$.paper[?(@.category=='ADULT')].entered").value(3))
            .andExpect(jsonPath("$.walkIn.total").value(5))
            .andExpect(jsonPath("$.walkIn.date").doesNotExist())
            .andExpect(jsonPath("$.totals.entered").value(4 + 1 + 5))
            .andExpect(jsonPath("$.uniqueQr").value(4))
        // 어제만 보면 어제 것만 나온다
        summary("?date=$yesterday").andExpect(jsonPath("$.totals.entered").value(1 + 3)).andExpect(jsonPath("$.uniqueQr").value(1))

        // 전체 기간 시간대별에는 날짜별로 나뉜다
        val dates = hourlyBuckets("?all=true").map { it.first }.toSet()
        dates shouldBe setOf(today.toString(), yesterday.toString())
    }

    @Test
    fun `date와 all을 함께 보내면 모든 집계 조회가 400이다`() {
        val query = "?date=$today&all=true"
        summary(query).andExpect(status().isBadRequest)
        mockMvc.perform(admin(get("/attendance/summary/$EXPO/hourly$query"))).andExpect(status().isBadRequest)
        recent(query).andExpect(status().isBadRequest)
        scanners(query).andExpect(status().isBadRequest)
    }

    @Test
    fun `새 조회도 관리자만 쓸 수 있다`() {
        listOf("hourly", "recent", "scanners").forEach { path ->
            mockMvc.perform(get("/attendance/summary/$EXPO/$path")).andExpect(status().isUnauthorized)
            mockMvc
                .perform(get("/attendance/summary/$EXPO/$path").header("X-User-Id", "2").header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isForbidden)
        }
    }

    private fun hourlyBuckets(query: String = ""): List<Triple<String, Long, Long>> {
        val json =
            mockMvc
                .perform(admin(get("/attendance/summary/$EXPO/hourly$query")))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return Regex("\"date\":\"([0-9-]+)\",\"hour\":(\\d+),\"entered\":(-?\\d+),\"exited\":(\\d+)")
            .findAll(json)
            .map { Triple(it.groupValues[1], it.groupValues[3].toLong(), it.groupValues[4].toLong()) }
            .toList()
    }

    private fun admin(request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        request.header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN")

    private fun scanPaper(
        token: String,
        scanner: String,
    ) {
        mockMvc
            .perform(
                patch("/attendance/qr/$EXPO")
                    .header("X-Scanner-Name", scanner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$token"}"""),
            ).andExpect(status().isOk)
    }

    private fun exitPaper(
        token: String,
        scanner: String,
    ) {
        mockMvc
            .perform(
                patch("/attendance/exit/$EXPO")
                    .header("X-Scanner-Name", scanner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$token"}"""),
            ).andExpect(status().isOk)
    }

    private fun walkIn(
        body: String,
        scanner: String?,
    ): ResultActions {
        val request = admin(post("/walk-in-counts/$EXPO")).contentType(MediaType.APPLICATION_JSON).content(body)
        return mockMvc.perform(scanner?.let { request.header("X-Scanner-Name", it) } ?: request).andExpect(status().isOk)
    }

    private fun summary(query: String = ""): ResultActions = mockMvc.perform(admin(get("/attendance/summary/$EXPO$query")))

    private fun recent(query: String = ""): ResultActions = mockMvc.perform(admin(get("/attendance/summary/$EXPO/recent$query")))

    private fun scanners(query: String = ""): ResultActions = mockMvc.perform(admin(get("/attendance/summary/$EXPO/scanners$query")))

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a11"
    }

    private fun fieldEntry(
        request: RecordEntryReqDto,
        participantId: Long = requireNotNull(request.participantId),
    ) = FieldVisitFixtures.entry(jdbcTemplate, clock, request, participantId)
}
