package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import team.startup.expo.support.IntegrationTestSupport

/** 안정화 설정이 빠지지 않았는지 지킨다(#101, #103). */
class StabilityPropertiesTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `서버 종료는 graceful이고 종료 대기 시간이 설정되어 있다`() {
        environment.getProperty("server.shutdown") shouldBe "graceful"
        environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase") shouldBe "20s"
    }

    @Test
    fun `prometheus 메트릭을 노출하고 Eureka 메타데이터에 수집 정보를 올린다`() {
        environment.getProperty("management.endpoints.web.exposure.include") shouldBe "health,prometheus"
        environment.getProperty("eureka.instance.metadata-map.[prometheus.scrape]") shouldBe "true"
        environment.getProperty("eureka.instance.metadata-map.[prometheus.path]") shouldBe "/actuator/prometheus"
        // 분리 포트를 지정하지 않으면 서비스 포트와 같은 값이다
        environment.getProperty("management.server.port") shouldBe environment.getProperty("eureka.instance.metadata-map.[prometheus.port]")
    }

    @Test
    fun `GC 일시 정지 시간을 SLO 버킷 히스토그램으로 내보낸다`() {
        // GC 알림은 비동기로 기록되므로 버킷이 보일 때까지 잠깐 다시 긁는다
        System.gc()
        val bucket = Regex("""jvm_gc_pause_seconds_bucket\{[^}]*le="0\.05"[^}]*}""")
        val deadline = System.nanoTime() + 10_000_000_000
        var body = scrape()
        while (!bucket.containsMatchIn(body) && System.nanoTime() < deadline) {
            Thread.sleep(100)
            body = scrape()
        }
        bucket.containsMatchIn(body) shouldBe true
    }

    private fun scrape(): String =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andReturn()
            .response.contentAsString
}
