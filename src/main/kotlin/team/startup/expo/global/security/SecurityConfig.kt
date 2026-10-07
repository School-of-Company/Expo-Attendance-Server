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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import team.startup.expo.global.exception.ErrorResponse
import tools.jackson.databind.ObjectMapper

/**
 * 게이트웨이가 JWT를 검증하고 `X-User-Id`만 넘기므로 이 서비스는 사용자 토큰을 직접 파싱하지 않는다.
 * 서비스 간 호출(폼 서비스의 토큰 확인 등)은 게이트웨이를 거치지 않으므로 `/internal` 하위 경로에서
 * `X-Internal-Token`으로 인증한다(`InternalTokenAuthenticationFilter`). 명시하지 않은 경로는 전부 막아 두고,
 * 엔드포인트가 생기면 경로별 규칙을 여기에 더한다.
 *
 * 박람회 입장 스캔, 프로그램 출석 스캔, 종이 QR 발급·입구 스캔은 게이트웨이를 거쳐 오는 요청이다. 사용자 인증과
 * 권한은 게이트웨이가 맡으므로 여기서는 경로만 열어 둔다. 이 경로들은 게이트웨이 라우팅으로만 외부에 닿아야 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
        internalProperties: InternalProperties,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(InternalTokenAuthenticationFilter(internalProperties), UsernamePasswordAuthenticationFilter::class.java)
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
                    .requestMatchers(HttpMethod.POST, "/qr-tokens/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/attendance/*",
                        "/attendance/standard/*",
                        "/attendance/training/*",
                        "/attendance/qr/*",
                    ).permitAll()
                    .requestMatchers(InternalTokenAuthenticationFilter.INTERNAL_PATH_MATCHER)
                    .hasAuthority(InternalTokenAuthenticationFilter.SERVICE_AUTHORITY)
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
