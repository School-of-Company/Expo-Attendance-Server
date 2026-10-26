package team.startup.expo.global.logging

import ch.qos.logback.classic.Level
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import team.startup.expo.support.LogCapture
import java.io.IOException

/** DB 없이 도는 단위 테스트: 요청마다 접근 로그 한 줄(HttpAccess). */
class HttpAccessLogFilterSpec :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        /** 체인 안에서 DispatcherServlet처럼 경로 템플릿과 상태를 정한다 */
        fun chain(
            status: Int,
            route: String? = null,
            failure: Exception? = null,
        ) = object : MockFilterChain() {
            override fun doFilter(
                request: ServletRequest,
                response: ServletResponse,
            ) {
                route?.let { request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, it) }
                (response as HttpServletResponse).status = status
                failure?.let { throw it }
            }
        }

        fun run(
            request: MockHttpServletRequest,
            chain: MockFilterChain,
            enabled: Boolean = true,
        ) = LogCapture(HttpAccessLogFilter.LOGGER_NAME).use { capture ->
            runCatching { HttpAccessLogFilter(enabled).doFilter(request, MockHttpServletResponse(), chain) }
            capture.events
        }

        given("HttpAccessLogFilter가") {
            `when`("경로 템플릿이 매칭된 요청이 성공하면") {
                val request = MockHttpServletRequest("PATCH", "/attendance/expo-1")
                request.queryString = "phoneNumber=01012345678"
                val events = run(request, chain(200, "/attendance/{expoId}"))

                then("템플릿, 상태, 소요 시간을 INFO 한 줄로 남기고 실제 값과 쿼리는 남기지 않는다") {
                    events shouldHaveSize 1
                    events.single().level shouldBe Level.INFO
                    events.single().loggerName shouldBe "HttpAccess"
                    val message = events.single().formattedMessage
                    message shouldMatch Regex("""PATCH /attendance/\{expoId} status: 200 \(\d+ms\)""")
                    message shouldNotContain "expo-1"
                    message shouldNotContain "01012345678"
                }
            }

            `when`("4xx면") {
                val events = run(MockHttpServletRequest("PATCH", "/attendance/expo-1"), chain(400, "/attendance/{expoId}"))

                then("WARN으로 남긴다") {
                    events shouldHaveSize 1
                    events.single().level shouldBe Level.WARN
                    events.single().formattedMessage shouldMatch Regex("""PATCH /attendance/\{expoId} status: 400 \(\d+ms\)""")
                }
            }

            `when`("보안 필터가 막아 경로 템플릿이 없으면") {
                val request = MockHttpServletRequest("GET", "/qr-tokens/e1")
                request.queryString = "token=secret"
                val events = run(request, chain(403))

                then("쿼리를 뺀 요청 URI로 WARN을 남긴다") {
                    events shouldHaveSize 1
                    events.single().level shouldBe Level.WARN
                    events.single().formattedMessage shouldMatch Regex("""GET /qr-tokens/e1 status: 403 \(\d+ms\)""")
                    events.single().formattedMessage shouldNotContain "secret"
                }
            }

            `when`("템플릿이 없는 URI가 200자를 넘으면") {
                val events = run(MockHttpServletRequest("GET", "/" + "a".repeat(300)), chain(404))

                then("200자로 자른다") {
                    events.single().formattedMessage shouldMatch Regex("""GET /a{199} status: 404 \(\d+ms\)""")
                }
            }

            `when`("5xx면") {
                val events = run(MockHttpServletRequest("PATCH", "/attendance/e1"), chain(503, "/attendance/{expoId}"))

                then("ERROR로 남긴다") {
                    events shouldHaveSize 1
                    events.single().level shouldBe Level.ERROR
                    events.single().formattedMessage shouldMatch Regex("""PATCH /attendance/\{expoId} status: 503 \(\d+ms\)""")
                }
            }

            `when`("처리 중 예외가 밖으로 나오면") {
                val request = MockHttpServletRequest("PATCH", "/attendance/e1")
                val events = run(request, chain(200, "/attendance/{expoId}", IllegalStateException("boom")))

                then("500을 ERROR로 한 번 남긴다") {
                    events shouldHaveSize 1
                    events.single().level shouldBe Level.ERROR
                    events.single().formattedMessage shouldMatch Regex("""PATCH /attendance/\{expoId} status: 500 \(\d+ms\)""")
                }
            }

            `when`("클라이언트가 끊어 응답 쓰기가 IOException으로 실패하면") {
                val request = MockHttpServletRequest("GET", "/attendance/summary/e1")
                val chain = chain(200, "/attendance/summary/{expoId}", IOException("Broken pipe"))
                val captured =
                    LogCapture(HttpAccessLogFilter.LOGGER_NAME).use { capture ->
                        shouldThrow<IOException> { HttpAccessLogFilter(true).doFilter(request, MockHttpServletResponse(), chain) }
                        capture.events
                    }

                then("499를 ERROR로 한 번만 남기고 예외는 그대로 던진다") {
                    captured shouldHaveSize 1
                    captured.single().level shouldBe Level.ERROR
                    captured.single().formattedMessage shouldMatch Regex("""GET /attendance/summary/\{expoId} status: 499 \(\d+ms\)""")
                }
            }

            `when`("actuator나 API 문서 경로면") {
                val paths =
                    listOf(
                        "/actuator/health/readiness",
                        "/actuator/prometheus",
                        "/attendance/swagger-ui.html",
                        "/attendance/swagger-ui/index.html",
                        "/attendance/v3/api-docs",
                    )
                val events = paths.flatMap { run(MockHttpServletRequest("GET", it), chain(200)) }

                then("남기지 않는다") {
                    events shouldHaveSize 0
                }
            }

            `when`("logging.http-access.enabled가 꺼져 있으면") {
                val events = run(MockHttpServletRequest("PATCH", "/attendance/e1"), chain(200, "/attendance/{expoId}"), enabled = false)

                then("남기지 않는다") {
                    events shouldHaveSize 0
                }
            }
        }
    })
