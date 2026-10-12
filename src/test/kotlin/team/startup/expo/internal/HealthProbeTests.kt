package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.service.impl.CancellationAbortedCleanupScheduler
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport

/** 준비·생존 프로브가 응답하고, 다른 서비스가 내려가 있어도 준비 상태에 영향이 없다(#105). */
class HealthProbeTests : IntegrationTestSupport() {
    @MockitoBean
    lateinit var userClient: UserClient

    @MockitoBean
    lateinit var expoClient: ExpoClient

    @MockitoBean
    lateinit var applicationClient: ApplicationClient

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var environment: Environment

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `기존 헬스체크 경로와 두 프로브가 인증 없이 UP으로 응답한다`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            mockMvc
                .perform(get(path))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("UP"))
        }
    }

    @Test
    fun `준비 상태는 DB만 보고 다른 서비스는 보지 않으며 생존 상태는 프로세스 상태만 본다`() {
        environment.getProperty("management.endpoint.health.probes.enabled") shouldBe "true"
        environment.getProperty("management.endpoint.health.group.readiness.include") shouldBe "readinessState,db"
        environment.getProperty("management.endpoint.health.group.liveness.include") shouldBe "livenessState"
    }

    @Test
    fun `유저 박람회 신청 서비스가 모두 실패해도 준비 상태는 UP이다`() {
        val down = RuntimeException("down")
        doThrow(down).`when`(userClient).getTraineeNames(TraineeNamesReqDto("expo-1", listOf(1L)))
        doThrow(down).`when`(expoClient).getPeriod(org.mockito.ArgumentMatchers.anyString())

        mockMvc
            .perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
        mockMvc
            .perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `프로브는 GET만 열려 있다`() {
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/actuator/health/readiness"),
            ).andExpect(status().is4xxClientError)
    }

    @Test
    fun `중단된 취소 기록 정리 작업은 보관일을 지정하지 않으면 등록되지 않는다`() {
        context.getBeansOfType(CancellationAbortedCleanupScheduler::class.java).isEmpty() shouldBe true
    }
}
