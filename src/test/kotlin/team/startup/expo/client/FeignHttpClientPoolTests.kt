package team.startup.expo.client

import feign.Client
import feign.hc5.ApacheHttp5Client
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties
import team.startup.expo.support.IntegrationTestSupport

/** Feign이 JDK 기본 클라이언트가 아니라 풀 기반 Apache HttpClient 5를 쓰고, 풀 설정이 반영되는지 확인한다(#256). */
class FeignHttpClientPoolTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var client: Client

    @Autowired
    private lateinit var properties: FeignHttpClientProperties

    @Test
    fun `Feign 클라이언트는 Apache HttpClient 5이다`() {
        client.shouldBeInstanceOf<ApacheHttp5Client>()
    }

    @Test
    fun `연결 풀 설정이 기본값으로 반영된다`() {
        properties.maxConnections shouldBe 200
        properties.maxConnectionsPerRoute shouldBe 50
        properties.timeToLive shouldBe 900L
    }
}
