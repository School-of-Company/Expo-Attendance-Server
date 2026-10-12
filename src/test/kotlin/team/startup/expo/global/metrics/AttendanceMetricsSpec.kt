package team.startup.expo.global.metrics

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeNaN
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport

/** DB 없이 도는 단위 테스트: 다른 서비스 호출, 취소 보호 락 대기, `PENDING` 입장 수 지표(#101). */
class AttendanceMetricsSpec :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val registry = SimpleMeterRegistry()
        beforeTest { AttendanceMetrics.use(registry) }
        afterTest { AttendanceMetrics.use(null) }

        fun callTimer(
            service: String,
            outcome: String,
        ) = registry
            .find("attendance.downstream.calls")
            .tag("service", service)
            .tag("outcome", outcome)
            .timer()

        given("다른 서비스 호출을 callService로 감쌀 때") {
            val breaker = UnitTestSupport.circuitBreaker("user")

            `when`("호출이 성공하면") {
                val result = breaker.callService("유저") { "ok" }

                then("결과를 돌려주고 user/success 타이머가 한 번 기록된다") {
                    result shouldBe "ok"
                    callTimer("user", "success")!!.count() shouldBe 1
                }
            }

            `when`("상대가 404로 답하면") {
                then("지정한 예외를 던지고 not_found로 기록된다") {
                    val e =
                        shouldThrow<ExpectedException> {
                            breaker.callService(
                                "박람회",
                                ExpectedException(HttpStatus.NOT_FOUND, "없음"),
                            ) { throw UnitTestSupport.feignNotFound() }
                        }
                    e.status shouldBe HttpStatus.NOT_FOUND
                    callTimer("expo", "not_found")!!.count() shouldBe 1
                }
            }

            `when`("상대가 409로 답하고 onConflict가 있으면") {
                then("onConflict의 예외를 던지고 conflict로 기록된다") {
                    val e =
                        shouldThrow<ExpectedException> {
                            breaker.callService(
                                "신청",
                                onConflict = { throw ExpectedException(HttpStatus.BAD_REQUEST, "이미 있음") },
                            ) { throw UnitTestSupport.feignConflict() }
                        }
                    e.status shouldBe HttpStatus.BAD_REQUEST
                    callTimer("application", "conflict")!!.count() shouldBe 1
                }
            }

            `when`("상대가 5xx로 실패하면") {
                then("503으로 바꾸고 failure로 기록된다") {
                    val e = shouldThrow<ExpectedException> { breaker.callService("유저") { throw UnitTestSupport.feignServerError() } }
                    e.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    callTimer("user", "failure")!!.count() shouldBe 1
                }
            }

            `when`("회로 차단기가 열려 있으면") {
                breaker.transitionToOpenState()

                then("상대를 부르지 않고 503이며 circuit_open으로 기록된다") {
                    var called = false
                    val e = shouldThrow<ExpectedException> { breaker.callService("유저") { called = true } }
                    e.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    called shouldBe false
                    callTimer("user", "circuit_open")!!.count() shouldBe 1
                }
            }

            `when`("알려진 이름이 아닌 서비스면") {
                CircuitBreaker.ofDefaults("other").callService("기타") { 1 }

                then("받은 이름을 그대로 태그로 쓴다") {
                    callTimer("기타", "success")!!.count() shouldBe 1
                }
            }
        }

        given("레지스트리가 없을 때") {
            then("지표를 기록하지 않아도 호출은 그대로 동작한다") {
                AttendanceMetrics.use(null)
                shouldNotThrowAny { UnitTestSupport.circuitBreaker().callService("유저") { "ok" } } shouldBe "ok"
                registry.meters.isEmpty() shouldBe true
            }
        }

        given("취소 보호 게이트로 스캔 락을 얻을 때") {
            val repository = mockk<CancellationGuardRepository>(relaxed = true)
            every { repository.findByExpoIdAndParticipantId(any(), any()) } returns null
            val gate = CancellationGuardGate(UnitTestSupport.transactionManager(), repository)

            `when`("스캔이 끝나면") {
                gate.scanning("expo-1", participantId = 7L) { "done" }

                then("락 대기 시간이 kind=scan으로 기록된다") {
                    registry
                        .find("attendance.guard.lock.wait")
                        .tag("kind", "scan")
                        .timer()!!
                        .count() shouldBe 1
                }
            }
        }

        given("PENDING 종이 QR 입장 수 게이지를 등록할 때") {
            val repository = mockk<QrEntryRepository>()

            `when`("조회가 성공하면") {
                every { repository.countPending() } returns 3L
                PendingQrEntryMetrics().pendingQrEntryMetricsBinder(repository).bindTo(registry)

                then("건수를 그대로 보여 준다") {
                    registry.get("attendance.qr.entry.pending").gauge().value() shouldBe 3.0
                }
            }

            `when`("조회가 실패하면") {
                every { repository.countPending() } throws IllegalStateException("db down")
                PendingQrEntryMetrics().pendingQrEntryMetricsBinder(repository).bindTo(registry)

                then("0이 아니라 NaN을 보여 준다") {
                    registry
                        .get("attendance.qr.entry.pending")
                        .gauge()
                        .value()
                        .shouldBeNaN()
                }
            }
        }
    })
