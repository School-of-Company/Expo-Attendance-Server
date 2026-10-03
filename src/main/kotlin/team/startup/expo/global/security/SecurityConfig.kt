package team.startup.expo.global.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import team.startup.expo.global.exception.ErrorResponse
import tools.jackson.databind.ObjectMapper

/**
 * 아직 열어줄 엔드포인트가 없어 actuator만 허용하고 나머지는 전부 막아둔다. 게이트웨이가 JWT를
 * 검증하고 `X-User-Id`만 넘기므로 이 서비스는 토큰을 직접 파싱하지 않는다 — 엔드포인트가
 * 생기면 경로별로 `permitAll`/인증 규칙을 추가한다. 서비스 간 호출(폼 서비스의 토큰 확인 등)은
 * 게이트웨이를 거치지 않으므로 별도 인증 방식을 정해야 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        writeError(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.")
                    }.accessDeniedHandler { _, response, _ ->
                        writeError(objectMapper, response, HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.")
                    }
            }.authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .denyAll()
            }

        return http.build()
    }

    private fun writeError(
        objectMapper: ObjectMapper,
        response: HttpServletResponse,
        status: Int,
        message: String,
    ) {
        response.status = status
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ErrorResponse(status = status, message = message))
    }
}
