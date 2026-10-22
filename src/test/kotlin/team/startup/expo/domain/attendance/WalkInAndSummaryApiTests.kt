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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.FieldVisitFixtures
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class WalkInAndSummaryApiTests : IntegrationTestSupport() {
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

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        FieldVisitFixtures.clear(jdbcTemplate)
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_walk_in_log, tb_entry_log, tb_entry_event, tb_qr_entry, tb_qr_token, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
    }

    @Test
    fun `간편입장을 구분별로 세고 취소하며 0 아래로 내려가지 않는다`() {
        repeat(3) { walkIn("""{"category":"ADULT"}""").andExpect(status().isOk) }
        walkIn("""{"category":"STUDENT","action":"ADD"}""").andExpect(status().isOk)

        walkIn("""{"category":"ADULT","action":"UNDO"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.counts[?(@.category=='ADULT')].count").value(2))
            .andExpect(jsonPath("$.counts[?(@.category=='STUDENT')].count").value(1))
            .andExpect(jsonPath("$.total").value(3))

        // 어린이·학생은 하나뿐이라 두 번째 취소는 거절한다
        walkIn("""{"category":"STUDENT","action":"UNDO"}""").andExpect(status().isOk)
        walkIn("""{"category":"STUDENT","action":"UNDO"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("더 줄일 수 없습니다. 오늘 이 구분의 집계가 없습니다."))
    }

    @Test
    fun `같은 requestId의 재전송은 한 번만 센다`() {
        val body = """{"category":"ADULT","requestId":"req-0001"}"""
        walkIn(body).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
        walkIn(body).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
        walkIn("""{"category":"ADULT","requestId":"req-0002"}""").andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `같은 requestId를 다른 박람회에 보내면 박람회마다 한 번씩 센다`() {
        val other = "7c2d9a4e-1b3f-4a68-8e05-3f6b9d1c2e77"
        val body = """{"category":"ADULT","requestId":"req-shared"}"""
        walkIn(body).andExpect(jsonPath("$.total").value(1))
        walkIn(body, other).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
        // 같은 박람회의 재전송은 여전히 한 번만 센다
        walkIn(body).andExpect(jsonPath("$.total").value(1))
        walkIn(body, other).andExpect(jsonPath("$.total").value(1))

        // requestId 없는 요청은 여러 번 센다
        repeat(2) { walkIn("""{"category":"ADULT"}""", other).andExpect(status().isOk) }
        walkIn("""{"category":"ADULT"}""", other).andExpect(jsonPath("$.total").value(4))

        // UNDO의 재전송은 박람회 안에서 한 번만 줄인다
        val undo = """{"category":"ADULT","action":"UNDO","requestId":"req-shared-undo"}"""
        walkIn(undo, other).andExpect(jsonPath("$.total").value(3))
        walkIn(undo, other).andExpect(jsonPath("$.total").value(3))
        walkIn(undo).andExpect(jsonPath("$.total").value(0))
        walkIn(undo).andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `집계를 0으로 만든 UNDO의 재전송은 오류 없이 현재 합계를 돌려준다`() {
        walkIn("""{"category":"ADULT"}""").andExpect(status().isOk)
        val undo = """{"category":"ADULT","action":"UNDO","requestId":"req-undo-1"}"""
        walkIn(undo).andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
        walkIn(undo).andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `여러 스캐너가 동시에 눌러도 유실 없이 센다`() {
        val executor = Executors.newFixedThreadPool(10)
        val tasks = List(40) { Callable { walkIn("""{"category":"STUDENT"}""").andReturn().response.status } }
        executor.invokeAll(tasks).map { it.get() }.toSet() shouldBe setOf(200)
        executor.shutdown()

        walkInGet().andExpect(jsonPath("$.counts[?(@.category=='STUDENT')].count").value(40)).andExpect(jsonPath("$.total").value(40))
    }

    @Test
    fun `같은 requestId를 동시에 보내도 한 번만 센다`() {
        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val statuses =
            List(threads) {
                executor.submit(
                    Callable {
                        start.await()
                        walkIn("""{"category":"ADULT","requestId":"req-race-1"}""").andReturn().response.status
                    },
                )
            }
        start.countDown()
        statuses.map { it.get() }.toSet() shouldBe setOf(200)
        executor.shutdown()

        walkInGet().andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun `집계보다 많은 UNDO를 동시에 보내도 0 아래로 내려가지 않는다`() {
        repeat(3) { walkIn("""{"category":"ADULT"}""").andExpect(status().isOk) }
        val threads = 10
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val statuses =
            List(threads) {
                executor.submit(
                    Callable {
                        start.await()
                        walkIn("""{"category":"ADULT","action":"UNDO"}""").andReturn().response.status
                    },
                )
            }
        start.countDown()
        val results = statuses.map { it.get() }
        executor.shutdown()

        results.count { it == 200 } shouldBe 3
        results.count { it == 400 } shouldBe threads - 3
        walkInGet().andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `참가 유형 8종은 각각 ADD와 UNDO가 구분별로 동작하고 0 아래로 내려가지 않는다`() {
        PARTICIPANT_TYPES.forEach { type ->
            walkIn("""{"category":"$type","action":"UNDO"}""").andExpect(status().isBadRequest)
            repeat(2) { walkIn("""{"category":"$type"}""").andExpect(status().isOk) }
            walkIn("""{"category":"$type","action":"UNDO"}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts[?(@.category=='$type')].count").value(1))
        }
        walkInGet()
            .andExpect(jsonPath("$.counts.length()").value(10))
            .andExpect(jsonPath("$.counts[?(@.category=='ADULT')].count").value(0))
            .andExpect(jsonPath("$.total").value(PARTICIPANT_TYPES.size))
        PARTICIPANT_TYPES.forEach { walkIn("""{"category":"$it","action":"UNDO"}""").andExpect(status().isOk) }
        walkInGet().andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `참가 유형 8종도 같은 requestId는 한 번만 센다`() {
        PARTICIPANT_TYPES.forEach { type ->
            val body = """{"category":"$type","requestId":"req-$type"}"""
            walkIn(body).andExpect(status().isOk)
            walkIn(body).andExpect(status().isOk).andExpect(jsonPath("$.counts[?(@.category=='$type')].count").value(1))
        }
        walkInGet().andExpect(jsonPath("$.total").value(PARTICIPANT_TYPES.size))
    }

    @Test
    fun `참가 유형 8종을 동시에 눌러도 구분별로 유실 없이 센다`() {
        val executor = Executors.newFixedThreadPool(10)
        val tasks =
            PARTICIPANT_TYPES.flatMap { type ->
                List(10) { Callable { walkIn("""{"category":"$type"}""").andReturn().response.status } }
            }
        executor.invokeAll(tasks).map { it.get() }.toSet() shouldBe setOf(200)
        executor.shutdown()

        PARTICIPANT_TYPES.forEach { walkInGet().andExpect(jsonPath("$.counts[?(@.category=='$it')].count").value(10)) }
        walkInGet().andExpect(jsonPath("$.total").value(PARTICIPANT_TYPES.size * 10))
    }

    @Test
    fun `간편입장 요약에 참가 유형이 나온다`() {
        walkIn("""{"category":"PRE_SERVICE_TEACHER"}""")
        walkIn("""{"category":"MIDDLE_SCHOOL_STUDENT"}""")
        walkIn("""{"category":"MIDDLE_SCHOOL_STUDENT"}""")
        mockMvc
            .perform(admin(get("/attendance/summary/$EXPO")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.walkIn.counts[?(@.category=='MIDDLE_SCHOOL_STUDENT')].count").value(2))
            .andExpect(jsonPath("$.walkIn.counts[?(@.category=='PRE_SERVICE_TEACHER')].count").value(1))
            .andExpect(jsonPath("$.walkIn.total").value(3))
    }

    @Test
    fun `관리자만 쓸 수 있고 입력을 검증한다`() {
        mockMvc
            .perform(
                post("/walk-in-counts/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"category":"ADULT"}"""),
            ).andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                post("/walk-in-counts/$EXPO")
                    .header("X-User-Id", "2")
                    .header("X-User-Role", "ROLE_USER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"category":"ADULT"}"""),
            ).andExpect(status().isForbidden)

        listOf(
            """{}""",
            """{"category":"ELDER"}""",
            """{"category":"ADULT","action":"X"}""",
            """{"category":"ADULT","requestId":"bad id!"}""",
            """{"category":"CHILD"}""",
            """{"category":"school_staff"}""",
        ).forEach {
            walkIn(it).andExpect(status().isBadRequest)
        }
        walkIn("""{"category":"ADULT"}""", expo = "NOT-A-UUID").andExpect(status().isBadRequest)
    }

    @Test
    fun `기간 밖이면 거절하고 박람회 서비스 장애에는 입구를 막지 않으며 삭제된 박람회는 404이다`() {
        doReturn(ExpoPeriodResDto(today.plusDays(3).toString(), today.plusDays(5).toString())).`when`(expoClient).getPeriod(anyString())
        walkIn("""{"category":"ADULT"}""").andExpect(status().isBadRequest)

        doThrow(feignException(500)).`when`(expoClient).getPeriod(anyString())
        walkIn("""{"category":"ADULT"}""").andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))

        deleteExpoDataService.delete(EXPO)
        walkIn("""{"category":"ADULT"}""").andExpect(status().isNotFound)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_walk_in_log", Long::class.java) shouldBe 0L
    }

    @Test
    fun `입장 처리 건수는 등록 QR, 종이 QR 구분별, 간편입장을 모아 보여 주고 퇴장을 반영한다`() {
        // 종이 QR 성인 2명 입장, 한 명은 퇴장. 아이 1명 입장
        listOf("paper-a1" to "ADULT", "paper-a2" to "ADULT", "paper-c1" to "CHILD").forEach { (token, category) ->
            jdbcTemplate.update(
                "INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES (?, ?, ?, now())",
                token,
                EXPO,
                category,
            )
            mockMvc
                .perform(patch("/attendance/qr/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}"""))
                .andExpect(status().isOk)
        }
        mockMvc
            .perform(patch("/attendance/exit/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"paper-a1"}"""))
            .andExpect(status().isOk)

        // 등록 QR 일반 참가자 1명 입장
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
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":7001,"code":"codePPPPPPPPPPPPPPPPPP"}"""),
            ).andExpect(status().isOk)

        walkIn("""{"category":"ADULT"}""")
        walkIn("""{"category":"ADULT"}""")
        walkIn("""{"category":"STUDENT"}""")

        summary()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registered.standard").value(1))
            .andExpect(jsonPath("$.registered.trainee").value(0))
            .andExpect(jsonPath("$.paper[?(@.category=='ADULT')].entered").value(2))
            .andExpect(jsonPath("$.paper[?(@.category=='ADULT')].exited").value(1))
            .andExpect(jsonPath("$.paper[?(@.category=='CHILD')].entered").value(1))
            .andExpect(jsonPath("$.walkIn.total").value(3))
            .andExpect(jsonPath("$.totals.entered").value(7))
            .andExpect(jsonPath("$.totals.exited").value(1))
            .andExpect(jsonPath("$.totals.stillInside").value(3))
            .andExpect(jsonPath("$.generatedAt").exists())

        // 다른 날짜는 비어 있다
        summary("${today.minusDays(1)}").andExpect(jsonPath("$.totals.entered").value(0)).andExpect(jsonPath("$.paper.length()").value(0))
    }

    @Test
    fun `입장 처리 건수 조회도 관리자만 쓸 수 있고 날짜를 검증한다`() {
        mockMvc.perform(get("/attendance/summary/$EXPO")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/attendance/summary/$EXPO").header("X-User-Id", "2").header("X-User-Role", "ROLE_USER"))
            .andExpect(status().isForbidden)
        summary("not-a-date").andExpect(status().isBadRequest)
        mockMvc.perform(admin(get("/attendance/summary/NOT-A-UUID"))).andExpect(status().isBadRequest)
    }

    private fun admin(request: MockHttpServletRequestBuilder) = request.header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN")

    private fun walkIn(
        body: String,
        expo: String = EXPO,
    ): ResultActions = mockMvc.perform(admin(post("/walk-in-counts/$expo")).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun walkInGet(): ResultActions = mockMvc.perform(admin(get("/walk-in-counts/$EXPO")))

    private fun summary(date: String? = null): ResultActions =
        mockMvc.perform(admin(get("/attendance/summary/$EXPO" + (date?.let { "?date=$it" } ?: ""))))

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
        val PARTICIPANT_TYPES =
            listOf(
                "KINDERGARTEN_STUDENT",
                "ELEMENTARY_STUDENT",
                "MIDDLE_SCHOOL_STUDENT",
                "HIGH_SCHOOL_STUDENT",
                "PARENT",
                "GENERAL",
                "TEACHER",
                "PRE_SERVICE_TEACHER",
            )
    }

    private fun fieldEntry(
        request: RecordEntryReqDto,
        participantId: Long = requireNotNull(request.participantId),
    ) = FieldVisitFixtures.entry(jdbcTemplate, clock, request, participantId)
}
