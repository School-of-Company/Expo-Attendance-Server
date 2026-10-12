package team.startup.expo.global.logging

import ch.qos.logback.classic.Level
import feign.RequestTemplate
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import team.startup.expo.support.LogCapture

/** DB 없이 도는 단위 테스트: 요청 상관 ID 필터, Feign 헤더 전파, 스캔 거절 로그(#104). */
class RequestLoggingSpec :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        afterTest { RequestContext.KEYS.forEach { MDC.remove(it) } }

        fun runFilter(requestIdHeader: String?): Triple<MockHttpServletResponse, String?, String?> {
            val request = MockHttpServletRequest("PATCH", "/attendance/e1")
            requestIdHeader?.let { request.addHeader("X-Request-Id", it) }
            val response = MockHttpServletResponse()
            var inChain: String? = null
            val chain =
                object : MockFilterChain() {
                    override fun doFilter(
                        request: jakarta.servlet.ServletRequest,
                        response: jakarta.servlet.ServletResponse,
                    ) {
                        inChain = MDC.get("requestId")
                    }
                }
            RequestIdFilter().doFilter(request, response, chain)
            return Triple(response, inChain, MDC.get("requestId"))
        }

        given("RequestIdFilter가") {
            `when`("요청에 안전한 X-Request-Id가 있으면") {
                val (response, inChain, after) = runFilter("gw-123.abc_9")

                then("그 값을 MDC에 두고 응답 헤더로 돌려주며 끝나면 MDC를 비운다") {
                    inChain shouldBe "gw-123.abc_9"
                    response.getHeader("X-Request-Id") shouldBe "gw-123.abc_9"
                    after.shouldBeNull()
                }
            }

            `when`("요청에 ID가 없으면") {
                val (response, inChain, after) = runFilter(null)

                then("짧은 ID를 만들어 MDC와 응답 헤더에 두고 MDC를 비운다") {
                    inChain!!.length shouldBe 16
                    response.getHeader("X-Request-Id") shouldBe inChain
                    after.shouldBeNull()
                }
            }

            `when`("요청의 ID에 줄바꿈 같은 위험한 문자가 있으면") {
                val (response, inChain, _) = runFilter("a b\nFAKE LOG LINE")

                then("버리고 새로 만든다") {
                    inChain!!.length shouldBe 16
                    response.getHeader("X-Request-Id") shouldBe inChain
                }
            }

            `when`("체인이 예외를 던져도") {
                val request = MockHttpServletRequest()
                val failing =
                    object : MockFilterChain() {
                        override fun doFilter(
                            request: jakarta.servlet.ServletRequest,
                            response: jakarta.servlet.ServletResponse,
                        ): Unit = throw IllegalStateException("boom")
                    }
                runCatching { RequestIdFilter().doFilter(request, MockHttpServletResponse(), failing) }

                then("MDC를 비운다") {
                    MDC.get("requestId").shouldBeNull()
                }
            }
        }

        given("RequestIdFeignInterceptor가") {
            `when`("MDC에 요청 ID가 있으면") {
                MDC.put("requestId", "req-1")
                val template = RequestTemplate()
                RequestIdFeignInterceptor().apply(template)

                then("X-Request-Id 헤더로 싣는다") {
                    template.headers()["X-Request-Id"] shouldBe listOf("req-1")
                }
            }

            `when`("MDC에 요청 ID가 없으면") {
                val template = RequestTemplate()
                RequestIdFeignInterceptor().apply(template)

                then("헤더를 싣지 않는다") {
                    template.headers().containsKey("X-Request-Id") shouldBe false
                }
            }
        }

        given("업무 사유로 요청을 거절할 때") {
            val loggerName = "team.startup.expo.global.logging.ScanRejection"
            val request = MockHttpServletRequest("PATCH", "/attendance/expo-1")
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/attendance/{expoId}")
            RequestContext.bindScan("expo-1", 55L)

            `when`("4xx면") {
                LogCapture(loggerName).use { capture ->
                    logRejection(request, HttpStatus.BAD_REQUEST, "오늘 이미 입장했습니다.")

                    then("경로 템플릿, 상태, 박람회·참가자 ID, 사유를 info 한 줄로 남긴다") {
                        capture.events shouldHaveSize 1
                        capture.events.single().level shouldBe Level.INFO
                        val message = capture.messages.single()
                        message shouldContain "PATCH /attendance/{expoId}"
                        message shouldContain "status=400"
                        message shouldContain "expoId=expo-1"
                        message shouldContain "participantId=55"
                        message shouldContain "오늘 이미 입장했습니다."
                        message shouldNotContain "/attendance/expo-1"
                    }
                }
            }

            `when`("5xx면") {
                LogCapture(loggerName).use { capture ->
                    logRejection(request, HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.")

                    then("이 로그는 남기지 않는다(원인은 다른 서비스 호출 로그가 남긴다)") {
                        capture.events shouldHaveSize 0
                    }
                }
            }
        }
    })
