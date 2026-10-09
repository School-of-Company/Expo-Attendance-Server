package team.startup.expo.internal

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.support.IntegrationTestSupport

@Import(InternalSecurityTests.PingController::class)
class InternalSecurityTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    /** `/internal` 하위 경로에 실제 엔드포인트가 아직 없어 보안 규칙만 확인하는 용도다. */
    @RestController
    class PingController {
        @GetMapping("/internal/ping")
        fun ping() = "pong"

        @GetMapping("/not-internal/ping")
        fun notInternal() = "pong"
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.perform(get("/internal/ping")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `토큰이 틀리면 401이다`() {
        mockMvc
            .perform(get("/internal/ping").header("X-Internal-Token", "wrong-token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `토큰이 맞으면 통과한다`() {
        mockMvc
            .perform(get("/internal/ping").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
    }

    @Test
    fun `내부 토큰이 있어도 internal 밖의 경로는 열리지 않는다`() {
        mockMvc
            .perform(get("/not-internal/ping").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isUnauthorized)
    }
}
