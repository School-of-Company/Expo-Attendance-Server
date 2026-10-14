package team.startup.expo.domain.qr

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrTokenCode
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate

/** 종이 QR 연번 코드·발급 현황·다시 조회·분실 무효화(#132). */
class QrTokenSerialApiTests : IntegrationTestSupport() {
    @MockitoBean
    lateinit var expoClient: ExpoClient

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clock: Clock

    @Autowired
    @Qualifier("expoCircuitBreaker")
    lateinit var expoCircuitBreaker: CircuitBreaker

    @BeforeEach
    fun setUp() {
        expoCircuitBreaker.reset()
        val today = LocalDate.now(clock)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString()))
            .`when`(expoClient)
            .getPeriod(anyString())
    }

    @Test
    fun `발급하면 구분별 연번과 인쇄용 코드를 함께 돌려주고 이어서 발급하면 연번이 이어진다`() {
        val expoId = "a1000000-0000-4000-8000-000000000001"

        issue(expoId, 3, "ADULT")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].serial").value(1))
            .andExpect(jsonPath("$.items[0].code").value(QrTokenCode.of(QrCategory.ADULT, 1)))
            .andExpect(jsonPath("$.items[2].serial").value(3))
            .andExpect(jsonPath("$.tokens.length()").value(3))
        // 다른 구분은 1부터 다시 시작하고, 같은 구분은 이어진다
        issue(expoId, 2, "CHILD").andExpect(jsonPath("$.items[0].code").value(QrTokenCode.of(QrCategory.CHILD, 1)))
        issue(expoId, 2, "ADULT")
            .andExpect(jsonPath("$.items[0].serial").value(4))
            .andExpect(jsonPath("$.items[1].code").value(QrTokenCode.of(QrCategory.ADULT, 5)))
    }

    @Test
    fun `발급 현황은 구분별 사용 중과 무효화 수를 돌려준다`() {
        val expoId = "a1000000-0000-4000-8000-000000000002"
        issue(expoId, 5, "ADULT")
        issue(expoId, 2, "CHILD")
        revoke(expoId, QrTokenCode.of(QrCategory.ADULT, 2)).andExpect(status().isNoContent)

        mockMvc
            .perform(get("/qr-tokens/$expoId/summary").asAdmin())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(2))
            .andExpect(jsonPath("$.categories[0].category").value("ADULT"))
            .andExpect(jsonPath("$.categories[0].active").value(4))
            .andExpect(jsonPath("$.categories[0].revoked").value(1))
            .andExpect(jsonPath("$.categories[1].category").value("CHILD"))
            .andExpect(jsonPath("$.categories[1].active").value(2))
            .andExpect(jsonPath("$.categories[1].revoked").value(0))
    }

    @Test
    fun `시작 연번과 개수로 다시 조회하고 같은 토큰과 코드를 돌려준다`() {
        val expoId = "a1000000-0000-4000-8000-000000000003"
        val issued =
            issue(expoId, 5, "ADULT")
                .andReturn()
                .response.contentAsString
        val tokenOfThird = Regex("\"serial\":3,\"code\":\"[^\"]+\",\"token\":\"([^\"]+)\"").find(issued)!!.groupValues[1]

        mockMvc
            .perform(
                get("/qr-tokens/$expoId")
                    .param("category", "ADULT")
                    .param("fromSerial", "3")
                    .param("count", "2")
                    .asAdmin(),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].serial").value(3))
            .andExpect(jsonPath("$.items[0].token").value(tokenOfThird))
            .andExpect(jsonPath("$.items[0].code").value(QrTokenCode.of(QrCategory.ADULT, 3)))
            .andExpect(jsonPath("$.items[1].serial").value(4))
        // 범위에 발급분이 없으면 빈 목록이다
        mockMvc
            .perform(get("/qr-tokens/$expoId").param("category", "CHILD").asAdmin())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `다시 조회는 개수 120과 구분 필수를 지킨다`() {
        val expoId = "a1000000-0000-4000-8000-000000000004"

        mockMvc
            .perform(get("/qr-tokens/$expoId").param("category", "ADULT").param("count", "121").asAdmin())
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(
                get("/qr-tokens/$expoId").param("category", "ADULT").param("fromSerial", "0").asAdmin(),
            ).andExpect(status().isBadRequest)
        mockMvc.perform(get("/qr-tokens/$expoId").asAdmin()).andExpect(status().isBadRequest)
    }

    @Test
    fun `무효화한 QR은 입구에서 사용이 중지된 QR로 거부하고 무효화는 여러 번 불러도 같다`() {
        val expoId = "a1000000-0000-4000-8000-000000000005"
        val issued = issue(expoId, 2, "ADULT").andReturn().response.contentAsString
        val tokens = Regex("\"token\":\"([^\"]+)\"").findAll(issued).map { it.groupValues[1] }.toList()
        val lostCode = QrTokenCode.of(QrCategory.ADULT, 1)

        // 소문자로 입력해도 무효화된다. 두 번째 호출도 성공이다
        revoke(expoId, lostCode.lowercase()).andExpect(status().isNoContent)
        revoke(expoId, lostCode).andExpect(status().isNoContent)

        mockMvc
            .perform(scan(expoId, tokens[0]))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("사용이 중지된 QR입니다."))
        // 무효화하지 않은 QR은 그대로 입장한다
        mockMvc.perform(scan(expoId, tokens[1])).andExpect(status().isOk)
    }

    @Test
    fun `확인 문자가 틀리거나 없는 코드는 무효화하지 않는다`() {
        val expoId = "a1000000-0000-4000-8000-000000000006"
        issue(expoId, 1, "ADULT")
        val code = QrTokenCode.of(QrCategory.ADULT, 1)
        val wrong = code.dropLast(2) + if (code.takeLast(2) == "00") "01" else "00"

        revoke(expoId, wrong).andExpect(status().isBadRequest)
        revoke(expoId, QrTokenCode.of(QrCategory.ADULT, 99)).andExpect(status().isNotFound)
        // 다른 박람회에서는 같은 코드라도 찾지 못한다
        revoke("a1000000-0000-4000-8000-0000000000ff", code).andExpect(status().isNotFound)
        mockMvc.perform(get("/qr-tokens/$expoId/summary").asAdmin()).andExpect(jsonPath("$.categories[0].revoked").value(0))
    }

    @Test
    fun `조회와 무효화는 관리자만 쓸 수 있다`() {
        val expoId = "a1000000-0000-4000-8000-000000000007"
        val requests =
            listOf(
                get("/qr-tokens/$expoId/summary"),
                get("/qr-tokens/$expoId").param("category", "ADULT"),
                patch("/qr-tokens/$expoId/revoke").contentType(MediaType.APPLICATION_JSON).content("""{"code":"ADT-0001-00"}"""),
            )

        requests.forEach { request ->
            mockMvc.perform(request).andExpect(status().isUnauthorized)
        }
        requests.forEach { request ->
            mockMvc
                .perform(request.header("X-User-Id", "2").header("X-User-Role", "ROLE_STANDARD"))
                .andExpect(status().isForbidden)
        }
    }

    private fun issue(
        expoId: String,
        count: Int,
        category: String,
    ) = mockMvc.perform(
        post("/qr-tokens/$expoId")
            .asAdmin()
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"count": $count, "category": "$category"}"""),
    )

    private fun revoke(
        expoId: String,
        code: String,
    ) = mockMvc.perform(
        patch("/qr-tokens/$expoId/revoke")
            .asAdmin()
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"code": "$code"}"""),
    )

    private fun scan(
        expoId: String,
        token: String,
    ) = patch("/attendance/qr/$expoId")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"token": "$token"}""")

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.asAdmin() =
        header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN")
}
