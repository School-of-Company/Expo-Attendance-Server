package team.startup.expo.domain.attendance.support

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
            mockk(relaxed = true),
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

    private fun givenRecord(record: PreregisterSession?) = givenRecords(listOfNotNull(record))

    private fun givenRecords(records: List<PreregisterSession>) {
        every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns records
        records.filter { it.status == PreregisterSessionStatus.ACTIVE }.mapNotNull { it.sessionId }.forEach {
            givenSession(now.minusSeconds(600), now.plusSeconds(3600), it)
        }
    }

    private fun givenSession(
        startedAt: Instant,
        endedAt: Instant,
        sessionId: Long = 11L,
    ) {
        every { expoClient.getPreregisterSession(expoId, sessionId) } returns PreregisterSessionResDto(sessionId, startedAt, endedAt)
    }

    private fun check() = validator.check(expoId, participantId, verifyCode)

    init {
        given("전화번호 입장의 취소 상태 확인에서") {
            listOf(null, PreregisterSessionStatus.ACTIVE, PreregisterSessionStatus.CANCELLED).forEach { status ->
                `when`("기록 상태가 $status 이면") {
                    givenRecord(status?.let { record(status = it) })
                    then("오늘의 ACTIVE 회차만 허용하고 시간검사 없이 회차를 붙인다") {
                        if (status != PreregisterSessionStatus.ACTIVE) {
                            shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                                validator.checkNotCancelled(expoId, participantId)
                            }
                        } else {
                            validator.checkNotCancelled(expoId, participantId) shouldBe 11L
                        }
                        verify(
                            exactly =
                                if (status ==
                                    PreregisterSessionStatus.ACTIVE
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { expoClient.getPreregisterSession(any(), any()) }
                    }
                }
            }
        }
        given("락 안에서 고른 회차를 다시 확인할 때") {
            `when`("고른 회차가 아직 확정 상태이면") {
                givenRecords(listOf(record(sessionId = 11L), record(sessionId = 12L)))

                then("그 회차 ID를 돌려준다") {
                    validator.checkNotCancelled(expoId, participantId, 12L) shouldBe 12L
                }
            }

            `when`("고른 회차만 그 사이 취소되었으면") {
                givenRecords(listOf(record(sessionId = 11L), record(status = PreregisterSessionStatus.CANCELLED, sessionId = 12L)))

                then("다른 회차가 남아 있어도 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                        validator.checkNotCancelled(expoId, participantId, 12L)
                    }
                }
            }
        }
        given("여러 회차가 배정된 참가자의 QR 입장에서") {
            val morning = 11L
            val afternoon = 12L

            `when`("오전 회차 시간이면") {
                givenRecords(listOf(record(sessionId = morning), record(sessionId = afternoon)))
                givenSession(now.minusSeconds(600), now.plusSeconds(3600), morning)
                givenSession(now.plusSeconds(4 * 3600), now.plusSeconds(6 * 3600), afternoon)

                then("오전 회차로 입장한다") {
                    check() shouldBe morning
                }
            }

            `when`("오후 회차 시간이면") {
                givenRecords(listOf(record(sessionId = morning), record(sessionId = afternoon)))
                givenSession(now.minusSeconds(6 * 3600), now.minusSeconds(4 * 3600), morning)
                givenSession(now.minusSeconds(600), now.plusSeconds(3600), afternoon)

                then("오후 회차로 입장한다") {
                    check() shouldBe afternoon
                }
            }

            `when`("두 회차 사이라 어느 회차의 입장 시간도 아니면") {
                givenRecords(listOf(record(sessionId = morning), record(sessionId = afternoon)))
                givenSession(now.minusSeconds(3 * 3600), now.minusSeconds(3600), morning)
                givenSession(now.plusSeconds(3600), now.plusSeconds(3 * 3600), afternoon)

                then("입장 시간이 아니라고 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다.") { check() }
                }
            }

            `when`("모든 회차가 끝났으면") {
                givenRecords(listOf(record(sessionId = morning), record(sessionId = afternoon)))
                givenSession(now.minusSeconds(6 * 3600), now.minusSeconds(4 * 3600), morning)
                givenSession(now.minusSeconds(3 * 3600), now.minusSeconds(1), afternoon)

                then("이미 끝났다고 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "신청한 회차가 이미 끝났습니다.") { check() }
                }
            }

            `when`("두 회차의 입장 시간이 겹치면") {
                givenRecords(listOf(record(sessionId = afternoon), record(sessionId = morning)))
                // 오후 회차 입장 창(시작 30분 전)이 오전 회차 종료 전에 열린다
                givenSession(now.minusSeconds(3600), now.plusSeconds(600), morning)
                givenSession(now.plusSeconds(20 * 60), now.plusSeconds(3 * 3600), afternoon)

                then("시작이 이른 회차를 고른다") {
                    check() shouldBe morning
                }
            }

            `when`("오전 회차를 취소하고 오전 회차 시간에 찍으면") {
                givenRecords(
                    listOf(record(status = PreregisterSessionStatus.CANCELLED, sessionId = morning), record(sessionId = afternoon)),
                )
                givenSession(now.plusSeconds(4 * 3600), now.plusSeconds(6 * 3600), afternoon)

                then("취소된 회차로는 입장하지 못하고 오후 회차는 조회만 한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 30분 전부터 입장할 수 있습니다.") { check() }
                    verify(exactly = 0) { expoClient.getPreregisterSession(expoId, morning) }
                }
            }

            `when`("오전 회차를 취소하고 오후 회차 시간에 찍으면") {
                givenRecords(
                    listOf(record(status = PreregisterSessionStatus.CANCELLED, sessionId = morning), record(sessionId = afternoon)),
                )
                givenSession(now.minusSeconds(600), now.plusSeconds(3600), afternoon)

                then("오후 회차로 입장한다") {
                    check() shouldBe afternoon
                }
            }
        }
        given("사전등록 QR의 회차 시간 확인에서") {
            `when`("회차 기록이 없는 참가자이면") {
                givenRecord(null)

                then("미상 날짜를 오늘 권한으로 추정하지 않고 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { check() }
                    verifyCalls shouldBe 1
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

                then("통과하고 확인한 회차 ID를 돌려준다") {
                    check() shouldBe 11L
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
