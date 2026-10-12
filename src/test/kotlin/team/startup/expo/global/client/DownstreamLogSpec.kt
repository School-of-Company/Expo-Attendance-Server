package team.startup.expo.global.client

import ch.qos.logback.classic.Level
import feign.FeignException
import feign.Request
import feign.RequestTemplate
import feign.Response
import feign.codec.DecodeException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.LogCapture
import team.startup.expo.support.UnitTestSupport
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.ConnectException
import java.time.Duration

private const val PHONE = "010-1234-5678"
private const val CODE = "secret-code"
private const val BODY = """{"phoneNumber":"$PHONE","code":"$CODE"}"""

/** 응답 해석에 실패하게 만드는 대상 타입 */
data class DecodeTarget(
    val id: Long,
)

private fun request(): Request =
    Request.create(
        Request.HttpMethod.POST,
        "http://user/internal/entries?phone=$PHONE&code=$CODE",
        mapOf("X-Internal-Token" to listOf("token-value")),
        BODY.toByteArray(),
        Charsets.UTF_8,
        RequestTemplate(),
    )

private fun errorResponse(): Response =
    Response
        .builder()
        .status(500)
        .reason("Internal Server Error")
        .request(request())
        .headers(emptyMap())
        .body(BODY, Charsets.UTF_8)
        .build()

/** DB 없이 도는 단위 테스트: 다른 서비스 호출 실패 로그에 원인은 있고 개인정보와 본문은 없다(#104). */
class DownstreamLogSpec :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val loggerName = "team.startup.expo.global.client.ServiceCall"

        given("5xx 응답에 개인정보가 담긴 본문이 있을 때") {
            val failure = FeignException.errorStatus("UserClient#recordEntry", errorResponse())
            val breaker = UnitTestSupport.circuitBreaker("user")

            `when`("callService가 503으로 바꾸면") {
                LogCapture(loggerName).use { capture ->
                    val e = shouldThrow<ExpectedException> { breaker.callService("유저") { throw failure } }

                    then("서비스, 예외, 상태 코드, 소요 시간이 warn으로 남고 본문·전화번호·코드는 없다") {
                        e.status.value() shouldBe 503
                        capture.events shouldHaveSize 1
                        capture.events.single().level shouldBe Level.WARN
                        val message = capture.messages.single()
                        message shouldContain "유저 서비스 호출 실패"
                        message shouldContain "status=500"
                        message shouldContain "elapsedMs="
                        message shouldContain "InternalServerError"
                        message shouldNotContain PHONE
                        message shouldNotContain CODE
                        message shouldNotContain "token-value"
                        message shouldNotContain "phoneNumber"
                    }
                }
            }
        }

        given("응답을 해석하지 못하는 DecodeException일 때") {
            val cause =
                runCatching {
                    jacksonObjectMapper().readValue("""{"id":"$CODE","name":"$PHONE"}""", DecodeTarget::class.java)
                }.exceptionOrNull()!!
            val failure = DecodeException(200, "decode failed: $BODY", request(), cause)

            `when`("로그를 남기면") {
                LogCapture(loggerName).use { capture ->
                    logDownstreamFailure("유저", failure, 5_000_000)

                    then("예외 종류, 원인 종류, 필드 경로는 있고 값은 없다") {
                        val message = capture.messages.single()
                        message shouldContain "DecodeException"
                        message shouldContain "status=200"
                        message shouldContain "elapsedMs=5"
                        message shouldContain "jsonPath=id"
                        message shouldNotContain PHONE
                        message shouldNotContain CODE
                        message shouldNotContain "http://"
                        message shouldNotContain "decode failed"
                    }
                }
            }
        }

        given("연결 실패 같은 Feign이 아닌 예외일 때") {
            `when`("원인이 ConnectException이면") {
                LogCapture(loggerName).use { capture ->
                    logDownstreamFailure("박람회", IllegalStateException("wrapped $PHONE", ConnectException("refused $CODE")), 1_000_000)

                    then("예외와 근본 원인 종류만 남기고 메시지는 남기지 않는다") {
                        val message = capture.messages.single()
                        message shouldContain "exception=IllegalStateException"
                        message shouldContain "rootCause=ConnectException"
                        message shouldNotContain PHONE
                        message shouldNotContain CODE
                    }
                }
            }
        }

        given("회로 차단기가 열려 호출이 막힐 때") {
            val breaker = UnitTestSupport.circuitBreaker("user")
            breaker.transitionToOpenState()

            `when`("호출이 여러 번 막히면") {
                LogCapture(loggerName, Level.INFO).use { capture ->
                    repeat(50) { shouldThrow<ExpectedException> { breaker.callService("유저") { "x" } } }

                    then("info 이상 로그는 하나도 남지 않는다") {
                        capture.events shouldHaveSize 0
                    }
                }
            }

            `when`("debug를 켜면") {
                LogCapture(loggerName, Level.DEBUG).use { capture ->
                    shouldThrow<ExpectedException> { breaker.callService("유저") { "x" } }

                    then("차단 한 줄이 debug로 남는다") {
                        capture.events.single().level shouldBe Level.DEBUG
                    }
                }
            }
        }

        given("상태 변경 로그를 단 회로 차단기가") {
            val breaker =
                CircuitBreaker
                    .of(
                        "user",
                        CircuitBreakerConfig
                            .custom()
                            .slidingWindowSize(4)
                            .minimumNumberOfCalls(4)
                            .failureRateThreshold(50f)
                            .waitDurationInOpenState(Duration.ofSeconds(60))
                            .build(),
                    ).logStateTransitions()

            `when`("실패로 열리고 그 뒤 호출이 수십 번 막히면") {
                LogCapture(loggerName, Level.INFO).use { capture ->
                    repeat(4) { runCatching { breaker.callService("유저") { throw UnitTestSupport.feignServerError() } } }
                    repeat(50) { runCatching { breaker.callService("유저") { "x" } } }

                    then("닫힘에서 열림으로 바뀔 때 warn 한 줄만 남고 실패율이 담긴다") {
                        breaker.state shouldBe CircuitBreaker.State.OPEN
                        val transitions = capture.events.filter { it.formattedMessage.contains("상태 변경") }
                        transitions shouldHaveSize 1
                        transitions.single().level shouldBe Level.WARN
                        transitions.single().formattedMessage shouldContain "CLOSED -> OPEN"
                        transitions.single().formattedMessage shouldContain "failureRate=100.0%"
                        // 실패한 호출 4번의 실패 로그와 상태 변경 1번뿐이고, 차단된 50번은 남지 않는다
                        capture.events.filter { it.level == Level.WARN } shouldHaveSize 5
                    }
                }
            }
        }
    })
