package team.startup.expo.domain.attendance.support

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.config.ExpoPeriodCacheProperties
import team.startup.expo.support.MutableClock
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.Duration
import java.time.LocalDate

class ExpoPeriodValidatorSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val expoClient = mockk<ExpoClient>()
    private val clock = MutableClock()
    private val expoId = "expo-1"

    private fun validator(ttlSeconds: Long = 30) =
        ExpoPeriodValidator(expoClient, UnitTestSupport.circuitBreaker("expo"), ExpoPeriodCacheProperties(ttlSeconds), clock)

    private fun givenPeriod(
        start: String = "2026-05-10",
        end: String = "2026-05-12",
    ) {
        every { expoClient.getPeriod(expoId) } returns ExpoPeriodResDto(start, end)
    }

    init {
        given("박람회 진행 여부 확인에서") {
            `when`("오늘이 시작일이면") {
                givenPeriod()
                then("통과한다(시작일 포함)") {
                    shouldNotThrowAny { validator().checkInProgress(expoId, LocalDate.of(2026, 5, 10)) }
                }
            }

            `when`("오늘이 종료일이면") {
                givenPeriod()
                then("통과한다(종료일 포함)") {
                    shouldNotThrowAny { validator().checkInProgress(expoId, LocalDate.of(2026, 5, 12)) }
                }
            }

            `when`("오늘이 시작일 전날이면") {
                givenPeriod()
                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") {
                        validator().checkInProgress(expoId, LocalDate.of(2026, 5, 9))
                    }
                }
            }

            `when`("오늘이 종료일 다음 날이면") {
                givenPeriod()
                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") {
                        validator().checkInProgress(expoId, LocalDate.of(2026, 5, 13))
                    }
                }
            }

            `when`("날짜를 넘기지 않으면") {
                givenPeriod(start = "2026-05-11", end = "2026-05-12")
                then("시계의 오늘(2026-05-10)로 판단한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") { validator().checkInProgress(expoId) }
                }
            }

            `when`("박람회 서비스가 404로 답하면") {
                every { expoClient.getPeriod(expoId) } throws UnitTestSupport.feignNotFound()
                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.") { validator().period(expoId) }
                }
            }

            `when`("박람회 서비스가 5xx로 답하면") {
                every { expoClient.getPeriod(expoId) } throws UnitTestSupport.feignServerError()
                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.") { validator().period(expoId) }
                }
            }

            `when`("박람회 서비스가 잘못된 날짜를 주면") {
                givenPeriod(start = "not-a-date")
                then("500이 아니라 503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.") { validator().period(expoId) }
                }
            }
        }

        given("박람회 기간 캐시에서") {
            `when`("TTL 안에 다시 읽으면") {
                givenPeriod()
                val v = validator(ttlSeconds = 30)
                v.period(expoId)
                clock.advance(Duration.ofSeconds(29))
                val second = v.period(expoId)

                then("박람회 서비스를 한 번만 부르고 같은 기간을 돌려준다") {
                    second shouldBe (LocalDate.of(2026, 5, 10) to LocalDate.of(2026, 5, 12))
                    verify(exactly = 1) { expoClient.getPeriod(expoId) }
                }
            }

            `when`("TTL이 지난 뒤 다시 읽으면") {
                givenPeriod()
                val v = validator(ttlSeconds = 30)
                v.period(expoId)
                clock.advance(Duration.ofSeconds(30))
                givenPeriod(end = "2026-05-20")
                val second = v.period(expoId)

                then("다시 불러 바뀐 기간을 본다") {
                    second shouldBe (LocalDate.of(2026, 5, 10) to LocalDate.of(2026, 5, 20))
                    verify(exactly = 2) { expoClient.getPeriod(expoId) }
                }
            }

            `when`("TTL이 0이면") {
                givenPeriod()
                val v = validator(ttlSeconds = 0)
                v.period(expoId)
                v.period(expoId)

                then("매번 박람회 서비스를 부른다") {
                    verify(exactly = 2) { expoClient.getPeriod(expoId) }
                }
            }

            `when`("첫 호출이 실패한 뒤 다시 읽으면") {
                every { expoClient.getPeriod(expoId) } throws UnitTestSupport.feignServerError()
                val v = validator()
                runCatching { v.period(expoId) }
                givenPeriod()
                val second = v.period(expoId)

                then("실패는 기억하지 않고 다시 불러 성공한다") {
                    second shouldBe (LocalDate.of(2026, 5, 10) to LocalDate.of(2026, 5, 12))
                    verify(exactly = 2) { expoClient.getPeriod(expoId) }
                }
            }

            `when`("서로 다른 박람회를 읽으면") {
                givenPeriod()
                every { expoClient.getPeriod("expo-2") } returns ExpoPeriodResDto("2026-06-01", "2026-06-02")
                val v = validator()
                v.period(expoId)
                val other = v.period("expo-2")

                then("박람회마다 따로 기억한다") {
                    other shouldBe (LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 6, 2))
                }
            }
        }
    }
}
