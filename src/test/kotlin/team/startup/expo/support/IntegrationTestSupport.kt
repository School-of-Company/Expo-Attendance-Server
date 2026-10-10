package team.startup.expo.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 실제 애플리케이션 컨텍스트를 PostgreSQL 컨테이너 위에서 띄운다. 같은 설정을 쓰는 테스트 클래스는
 * 컨텍스트와 컨테이너를 공유한다. 다른 서비스는 띄우지 않는다.
 */
@SpringBootTest(properties = ["eureka.client.enabled=false", "qr-entry-review.enabled=false", "expo-period-cache.ttl-seconds=0"])
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.ContainersConfig::class)
abstract class IntegrationTestSupport {
    @TestConfiguration(proxyBeanMethods = false)
    class ContainersConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
    }

    companion object {
        const val INTERNAL_TOKEN = "test-internal-token-0123456789abcdef"
        const val USER_INTERNAL_TOKEN = "test-user-internal-token-0123456789ab"
        const val EXPO_INTERNAL_TOKEN = "test-expo-internal-token-0123456789ab"
        const val APPLICATION_INTERNAL_TOKEN = "test-application-internal-token-0123456789"

        @JvmStatic
        @DynamicPropertySource
        fun serviceProperties(registry: DynamicPropertyRegistry) {
            registry.add("internal.token") { INTERNAL_TOKEN }
            registry.add("clients.user.internal-token") { USER_INTERNAL_TOKEN }
            registry.add("clients.expo.internal-token") { EXPO_INTERNAL_TOKEN }
            registry.add("clients.application.internal-token") { APPLICATION_INTERNAL_TOKEN }
        }
    }
}
