package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

class RequestIdTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `요청 ID가 없으면 만들어 응답 헤더로 돌려준다`() {
        val id =
            mockMvc
                .perform(get("/actuator/health"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getHeader("X-Request-Id")

        id!!.length shouldBe 16
    }

    @Test
    fun `게이트웨이가 넘긴 X-Request-Id는 그대로 돌려준다`() {
        mockMvc
            .perform(get("/actuator/health").header("X-Request-Id", "gw-abc-123"))
            .andExpect(header().string("X-Request-Id", "gw-abc-123"))
    }

    @Test
    fun `인증에 막힌 요청에도 요청 ID가 붙는다`() {
        mockMvc
            .perform(get("/internal/ping").header("X-Request-Id", "gw-denied-1"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("X-Request-Id", "gw-denied-1"))
    }

    @Test
    fun `스캔 요청이 검증에 막혀도 요청 ID가 붙는다`() {
        mockMvc
            .perform(
                patch("/attendance/standard/1")
                    .header("X-Request-Id", "scan-1")
                    .contentType("application/json")
                    .content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(header().string("X-Request-Id", "scan-1"))
    }
}
