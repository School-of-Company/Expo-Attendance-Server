package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import jakarta.servlet.AsyncEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockAsyncContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.service.impl.AdminDataChangeListener
import team.startup.expo.domain.attendance.service.impl.AdminDataChangeStream
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestPropertySource(
    properties = [
        "admin-events.enabled=true",
        "admin-events.heartbeat-interval=PT1H",
        "admin-events.max-connections-per-expo=3",
        "admin-events.coalesce-interval=PT0.3S",
    ],
)
class AdminDataChangeApiTests : IntegrationTestSupport() {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var stream: AdminDataChangeStream

    @Autowired private lateinit var listener: AdminDataChangeListener

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var clock: Clock

    @MockitoBean private lateinit var userClient: UserClient

    @MockitoBean private lateinit var expoClient: ExpoClient

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_walk_in_log, tb_entry_log, tb_entry_event RESTART IDENTITY CASCADE")
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        eventually { listener.isListening() }
        // 앞 테스트의 신호가 다 지나가도록 기다린다
        Thread.sleep(500)
    }

    @Test
    fun `관리자 역할만 구독할 수 있고 query와 잘못된 박람회 ID는 400이다`() {
        val before = stream.connectionCount()
        mockMvc
            .perform(get("/attendance/summary/$EXPO/events"))
            .andReturn()
            .response.status shouldBe 401
        mockMvc
            .perform(get("/attendance/summary/$EXPO/events").header("X-User-Id", "2").header("X-User-Role", "ROLE_USER"))
            .andReturn()
            .response.status shouldBe 403
        admin("/attendance/summary/$EXPO/events?access_token=jwt").response.status shouldBe 400
        admin("/attendance/summary/NOT-AN-ID/events").response.status shouldBe 400
        stream.connectionCount() shouldBe before

        val open = subscribe()
        open.request.isAsyncStarted shouldBe true
        open.response.contentType?.substringBefore(';') shouldBe "text/event-stream"
        open.response.getHeader("Cache-Control") shouldBe "no-store"
        open.response.getHeader("X-Accel-Buffering") shouldBe "no"
        close(open)
    }

    @Test
    fun `커밋된 입장은 그 박람회 관리자 둘에게 한 번씩 가고 다른 박람회는 조용하다`() {
        val first = subscribe()
        val second = subscribe()
        val outsider = subscribe(OTHER)
        insertEntry(EXPO, "1")

        eventually { events(first).size == 1 && events(second).size == 1 }
        Thread.sleep(700)
        events(outsider).size shouldBe 0
        val event = events(first).single()
        event.propertyNames().toSet() shouldBe setOf("eventId", "expoId", "resource", "occurredAt")
        event["expoId"].asString() shouldBe EXPO
        event["resource"].asString() shouldBe "ATTENDANCE"
        first.response.contentAsString.contains("id:${event["eventId"].asString()}") shouldBe true
        listOf(first, second, outsider).forEach(::close)
    }

    @Test
    fun `연속 스캔은 병합 간격 안에서 한 번으로 모이고 중복 입장과 롤백은 신호를 만들지 않는다`() {
        val result = subscribe()
        repeat(5) { insertEntry(EXPO, "burst-$it") }
        eventually { events(result).size == 1 }
        Thread.sleep(700)
        events(result).size shouldBe 1

        // 같은 날 두 번째 스캔은 행이 생기지 않는다
        insertEntry(EXPO, "burst-0")
        transactions.executeWithoutResult {
            insertEntry(EXPO, "rolled-back")
            it.setRollbackOnly()
        }
        Thread.sleep(1_500)
        events(result).size shouldBe 1
        close(result)
    }

    @Test
    fun `퇴장과 간편입장도 신호를 보내고 같은 요청의 재전송은 보내지 않는다`() {
        insertEntry(EXPO, "exit-1")
        Thread.sleep(700)
        val result = subscribe()

        jdbcTemplate.update("UPDATE tb_entry_log SET exited_at = now() WHERE expo_id = ? AND subject_id = 'exit-1'", EXPO)
        eventually { events(result).size == 1 }
        // 퇴장 시각이 그대로인 갱신은 신호가 아니다
        jdbcTemplate.update("UPDATE tb_entry_log SET scanner = 'x' WHERE expo_id = ? AND subject_id = 'exit-1'", EXPO)
        Thread.sleep(700)
        events(result).size shouldBe 1

        walkIn("""{"category":"ADULT","requestId":"req-1"}""")
        eventually { events(result).size == 2 }
        walkIn("""{"category":"ADULT","requestId":"req-1"}""")
        Thread.sleep(700)
        events(result).size shouldBe 2
        close(result)
    }

    @Test
    fun `DB 신호 연결이 끊겼다 복구되면 모든 구독자에게 재조회를 알린다`() {
        val result = subscribe()
        jdbcTemplate.queryForList(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query = 'LISTEN attendance_admin_changes' AND pid <> pg_backend_pid()",
        )
        eventually(seconds = 10) { events(result).size == 1 }
        eventually { listener.isListening() }
        insertEntry(EXPO, "after-reconnect")
        eventually { events(result).size == 2 }
        close(result)
    }

    @Test
    fun `닫히거나 수명이 끝난 연결은 정리되고 박람회별 한도가 적용된다`() {
        val closed = subscribe()
        val expired = subscribe()
        val kept = subscribe()
        subscribe().response.status shouldBe 503
        (closed.request.asyncContext as MockAsyncContext).complete()
        eventually { stream.connectionCount(EXPO) == 2 }
        val context = expired.request.asyncContext as MockAsyncContext
        context.listeners.forEach { it.onTimeout(AsyncEvent(context)) }
        eventually { stream.connectionCount(EXPO) == 1 }
        val again = subscribe()
        again.request.isAsyncStarted shouldBe true
        listOf(kept, again).forEach(::close)
    }

    private fun admin(path: String): MvcResult =
        mockMvc.perform(get(path).header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN")).andReturn()

    private fun subscribe(expoId: String = EXPO): MvcResult = admin("/attendance/summary/$expoId/events")

    private fun close(result: MvcResult) {
        (result.request.asyncContext as? MockAsyncContext)?.complete()
    }

    private fun walkIn(body: String) {
        mockMvc
            .perform(
                post("/walk-in-counts/$EXPO")
                    .header("X-User-Id", "1")
                    .header("X-User-Role", "ROLE_ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andReturn()
            .response.status shouldBe 200
    }

    private fun insertEntry(
        expoId: String,
        subjectId: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at) " +
                "VALUES (?, 'STANDARD', ?, ?, now()) ON CONFLICT (expo_id, subject_type, subject_id, attendance_date) DO NOTHING",
            expoId,
            subjectId,
            today,
        )
    }

    // 서버가 아직 쓰는 중이면 마지막 블록이 `data:` 줄 중간에서 끊겨 있다. 빈 줄로 끝난 완결 블록만 읽도록 마지막 조각은 버린다
    private fun events(result: MvcResult) =
        result.response.contentAsString
            .split("\n\n")
            .dropLast(1)
            .filter { it.contains("event:admin-data-changed") }
            .map { block -> mapper.readTree(block.lines().single { it.startsWith("data:") }.removePrefix("data:")) }

    private fun eventually(
        seconds: Long = 5,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    private companion object {
        const val EXPO = "11111111-2222-3333-4444-555555555555"
        const val OTHER = "99999999-2222-3333-4444-555555555555"
    }
}
