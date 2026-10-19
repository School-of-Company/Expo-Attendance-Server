package team.startup.expo.domain.attendance

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
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** #148: 종이 QR은 퇴장한 뒤에 다시 입장 스캔하면 재입장이고, 퇴장 없이 다시 찍으면 거절한다. */
class PaperQrReentryApiTests : IntegrationTestSupport() {
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
            "TRUNCATE TABLE tb_entry_log, tb_entry_event, tb_qr_entry, tb_qr_token, tb_deleted_expo RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        jdbcTemplate.update("INSERT INTO tb_qr_token (token, expo_id, category, created_at) VALUES (?, ?, 'ADULT', now())", TOKEN, EXPO)
    }

    @Test
    fun `첫 스캔은 최초 입장으로 응답한다`() {
        entry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("FIRST"))
            .andExpect(jsonPath("$.firstEnteredTime").exists())
    }

    @Test
    fun `퇴장 없이 다시 찍으면 400이고 퇴장한 뒤 다시 입장하면 재입장이며 몇 번이든 반복할 수 있다`() {
        entry().andExpect(status().isOk)
        // 퇴장하지 않고 다시 찍은 것은 재입장이 아니다
        entry().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("오늘 이미 입장한 QR입니다."))

        exit().andExpect(status().isOk)
        entry()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))
            .andExpect(jsonPath("$.firstEnteredTime").exists())
        // 재입장한 뒤에 또 찍으면 다시 400이고, 다시 퇴장할 수 있다
        entry().andExpect(status().isBadRequest)
        exit().andExpect(status().isOk)
        exit().andExpect(status().isBadRequest)
        entry().andExpect(status().isOk).andExpect(jsonPath("$.entryStatus").value("RE_ENTRY"))

        events() shouldBe listOf("ENTER", "EXIT", "RE_ENTER", "EXIT", "RE_ENTER")
        // 하루 최초 입장 행은 하나뿐이다
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_log", Long::class.java) shouldBe 1L
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_qr_entry", Long::class.java) shouldBe 1L
    }

    @Test
    fun `재입장은 입장 인원을 늘리지 않고 지금 안에 있는 인원에 다시 들어간다`() {
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)
        summary().andExpect(jsonPath("$.totals.entered").value(1)).andExpect(jsonPath("$.totals.stillInside").value(0))

        entry().andExpect(status().isOk)
        summary()
            .andExpect(jsonPath("$.totals.entered").value(1))
            .andExpect(jsonPath("$.totals.exited").value(0))
            .andExpect(jsonPath("$.totals.stillInside").value(1))
            .andExpect(jsonPath("$.uniqueQr").value(1))
    }

    @Test
    fun `퇴장 이력은 재입장해도 남는다`() {
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_entry_event WHERE event_type = 'EXIT'", Long::class.java) shouldBe 2L
    }

    @Test
    fun `퇴장한 종이 QR을 동시에 찍어도 재입장은 한 번만 기록된다`() {
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)

        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val statuses =
            List(threads) {
                executor.submit(
                    Callable {
                        start.await()
                        entry().andReturn().response.status
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

    @Test
    fun `무효화한 종이 QR은 퇴장한 뒤에도 재입장할 수 없다`() {
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)
        jdbcTemplate.update("UPDATE tb_qr_token SET revoked_at = now() WHERE token = ?", TOKEN)

        entry().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("사용이 중지된 QR입니다."))
        events() shouldBe listOf("ENTER", "EXIT")
    }

    @Test
    fun `재입장은 스캐너 이름과 함께 이력에 남는다`() {
        entry().andExpect(status().isOk)
        exit().andExpect(status().isOk)
        mockMvc
            .perform(
                patch("/attendance/qr/$EXPO")
                    .header("X-Scanner-Name", "gate-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$TOKEN"}"""),
            ).andExpect(status().isOk)

        jdbcTemplate.queryForObject("SELECT scanner FROM tb_entry_event WHERE event_type = 'RE_ENTER'", String::class.java) shouldBe
            "gate-2"
    }

    private fun events(): List<String> =
        jdbcTemplate.queryForList("SELECT event_type FROM tb_entry_event ORDER BY id", String::class.java).filterNotNull()

    private fun entry(): ResultActions =
        mockMvc.perform(patch("/attendance/qr/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$TOKEN"}"""))

    private fun exit(): ResultActions =
        mockMvc.perform(patch("/attendance/exit/$EXPO").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$TOKEN"}"""))

    private fun summary(): ResultActions =
        mockMvc.perform(get("/attendance/summary/$EXPO?date=$today").header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN"))

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10"
        const val TOKEN = "paper-token-0001"
    }
}
