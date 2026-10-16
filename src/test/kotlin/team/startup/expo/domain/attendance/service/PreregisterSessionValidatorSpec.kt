package team.startup.expo.domain.attendance.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterEntryProperties
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.Instant
import java.time.LocalDateTime

class PreregisterSessionValidatorSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val sessionRepository = mockk<PreregisterSessionRepository>()
    private val expoClient = mockk<ExpoClient>()
    private val clock = UnitTestSupport.fixedClock()
    private val now: Instant = clock.instant()

    private val validator =
        PreregisterSessionValidator(
            sessionRepository,
            expoClient,
            UnitTestSupport.circuitBreaker("expo"),
            PreregisterEntryProperties(leadMinutes = 30),
            clock,
        )

    private val expoId = "expo-1"
    private val participantId = 7L
    private var verifyCalls = 0
    private val verifyCode: () -> Unit = { verifyCalls++ }

    private fun record(
        status: PreregisterSessionStatus = PreregisterSessionStatus.ACTIVE,
        sessionId: Long? = 11L,
    ) = PreregisterSession(
        expoId = expoId,
        participantId = participantId,
        sessionId = sessionId,
        status = status,
        updatedAt = LocalDateTime.of(2026, 5, 10, 3, 0),
    )

    private fun givenRecord(record: PreregisterSession?) {
        every { sessionRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns record
    }

    private fun givenSession(
        startedAt: Instant,
        endedAt: Instant,
    ) {
        every { expoClient.getPreregisterSession(expoId, 11L) } returns PreregisterSessionResDto(11L, startedAt, endedAt)
    }

    private fun check() = validator.check(expoId, participantId, verifyCode)

    init {
        given("전화번호 입장의 취소 상태 확인에서") {
            listOf(null, PreregisterSessionStatus.ACTIVE, PreregisterSessionStatus.CANCELLED).forEach { status ->
                `when`("기록 상태가 $status 이면") {
                    givenRecord(status?.let { record(status = it) })
                    then("취소만 거절하고 회차는 조회하지 않는다") {
                        if (status == PreregisterSessionStatus.CANCELLED) {
                            shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                                validator.checkNotCancelled(expoId, participantId)
                            }
                        } else {
                            shouldNotThrowAny { validator.checkNotCancelled(expoId, participantId) }
                        }
                        verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                    }
                }
            }
        }
        given("사전등록 QR의 회차 시간 확인에서") {
            `when`("회차 기록이 없는 참가자이면") {
                givenRecord(null)

                then("아무것도 확인하지 않고 통과한다") {
                    shouldNotThrowAny { check() }
                    verifyCalls shouldBe 0
                    verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                }
            }

            `when`("신청이 취소된 참가자이면") {
                givenRecord(record(status = PreregisterSessionStatus.CANCELLED))

                then("코드를 확인한 뒤 400으로 거절하고 박람회 서비스를 부르지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { check() }
                    verifyCalls shouldBe 1
                    verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                }
            }

            `when`("코드 확인이 실패하면") {
                givenRecord(record(status = PreregisterSessionStatus.CANCELLED))

                then("취소 여부를 알리기 전에 코드 확인 에러가 먼저 난다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") {
                        validator.check(expoId, participantId) { throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") }
                    }
                }
            }

            `when`("확정 상태인데 회차 ID가 없으면") {
                givenRecord(record(sessionId = null))

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "신청한 회차를 확인할 수 없습니다.") { check() }
                }
            }

            `when`("박람회 서비스가 회차를 404로 답하면") {
                givenRecord(record())
                every { expoClient.getPreregisterSession(expoId, 11L) } throws UnitTestSupport.feignNotFound()

                then("없음이 아니라 호출 실패로 보아 503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.") { check() }
                }
            }

            `when`("박람회 서비스가 5xx로 답하면") {
                givenRecord(record())
                every { expoClient.getPreregisterSession(expoId, 11L) } throws UnitTestSupport.feignServerError()

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.") { check() }
                }
            }

            `when`("회차 시작 30분 전보다 1초 이르면") {
                givenRecord(record())
                givenSession(now.plusSeconds(30 * 60 + 1), now.plusSeconds(7200))

                then("400 입장 시간이 아니라는 메시지를 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다.") { check() }
                }
            }

            `when`("정확히 회차 시작 30분 전이면") {
                givenRecord(record())
                givenSession(now.plusSeconds(30 * 60), now.plusSeconds(7200))

                then("통과한다") {
                    shouldNotThrowAny { check() }
                }
            }

            `when`("회차가 진행 중이면") {
                givenRecord(record())
                givenSession(now.minusSeconds(600), now.plusSeconds(600))

                then("통과한다") {
                    shouldNotThrowAny { check() }
                }
            }

            `when`("회차 종료 시각과 정확히 같으면") {
                givenRecord(record())
                givenSession(now.minusSeconds(3600), now)

                then("통과한다") {
                    shouldNotThrowAny { check() }
                }
            }

            `when`("회차 종료 시각을 1초 넘기면") {
                givenRecord(record())
                givenSession(now.minusSeconds(3600), now.minusSeconds(1))

                then("400 이미 끝났다는 메시지를 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "신청한 회차가 이미 끝났습니다.") { check() }
                }
            }
        }
    }
}
