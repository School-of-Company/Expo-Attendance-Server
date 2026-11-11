package team.startup.expo.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties
import org.springframework.context.ApplicationContext
import team.startup.expo.support.IntegrationTestSupport

/** Feign이 JDK 기본 클라이언트가 아니라 풀 기반 Apache HttpClient 5를 쓰고, 풀 설정이 반영되는지 확인한다(#256). */
class FeignHttpClientPoolTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var properties: FeignHttpClientProperties

    @Test
    fun `Feign이 쓰는 HTTP 클라이언트 빈은 풀 기반 Apache HttpClient 5이다`() {
        // Client 빈은 로드밸런서 래퍼일 수 있어, 그 아래 hc5 클라이언트 빈이 만들어졌는지로 확인한다
        context.getBean(CloseableHttpClient::class.java).shouldBeInstanceOf<CloseableHttpClient>()
    }

    @Test
    fun `연결 풀 설정이 기본값으로 반영된다`() {
        properties.maxConnections shouldBe 200
        properties.maxConnectionsPerRoute shouldBe 50
        properties.timeToLive shouldBe 900L
    }
}
