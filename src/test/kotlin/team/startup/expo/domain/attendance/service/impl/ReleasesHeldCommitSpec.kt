package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.service.impl.CancellationGuardServiceImpl.Companion.releasesHeldCommit
import java.time.LocalDateTime

class ReleasesHeldCommitSpec :
    BehaviorSpec({
        fun record(
            sessionId: Long?,
            status: PreregisterSessionStatus,
            revision: Long = 0,
        ) = PreregisterSession(
            expoId = "expo-1",
            participantId = 7L,
            sessionId = sessionId,
            status = status,
            revision = revision,
            updatedAt = LocalDateTime.of(2026, 5, 10, 12, 0),
        )

        given("회차 기록이 없을 때") {
            then("revision과 회차가 있어도 확정한다") {
                releasesHeldCommit(emptyList(), revision = 5, sessionId = 1) shouldBe false
            }
        }

        given("revision 없는 전체 취소") {
            then("새 기록이 있어도 밀려난 것으로 보지 않고 확정한다") {
                val records = listOf(record(1, PreregisterSessionStatus.ACTIVE, revision = 9))
                releasesHeldCommit(records, revision = null, sessionId = null) shouldBe false
            }
        }

        given("revision 있는 전체 취소") {
            `when`("취소되지 않은 기록 중 더 새로운 revision이 있으면") {
                then("밀려난 취소라 해제한다") {
                    val records =
                        listOf(
                            record(1, PreregisterSessionStatus.CANCELLED, revision = 5),
                            record(2, PreregisterSessionStatus.ACTIVE, revision = 6),
                        )
                    releasesHeldCommit(records, revision = 5, sessionId = null) shouldBe true
                }
            }
            `when`("현장 재등록 기록이 더 새로우면") {
                then("밀려난 취소라 해제한다") {
                    val records = listOf(record(null, PreregisterSessionStatus.FIELD_REGISTERED, revision = 8))
                    releasesHeldCommit(records, revision = 5, sessionId = null) shouldBe true
                }
            }
            `when`("더 새로운 기록이 취소된 것뿐이면") {
                then("확정한다") {
                    val records = listOf(record(1, PreregisterSessionStatus.CANCELLED, revision = 9))
                    releasesHeldCommit(records, revision = 5, sessionId = null) shouldBe false
                }
            }
            `when`("같은 revision의 기록만 있으면") {
                then("더 새롭지 않으므로 확정한다") {
                    val records = listOf(record(1, PreregisterSessionStatus.ACTIVE, revision = 5))
                    releasesHeldCommit(records, revision = 5, sessionId = null) shouldBe false
                }
            }
        }

        given("회차 하나의 취소") {
            `when`("확정 상태인 다른 회차가 남아 있으면") {
                then("남은 회차로 입장할 수 있게 해제한다") {
                    val records =
                        listOf(
                            record(1, PreregisterSessionStatus.CANCELLED),
                            record(2, PreregisterSessionStatus.ACTIVE),
                        )
                    releasesHeldCommit(records, revision = null, sessionId = 1) shouldBe true
                }
            }
            `when`("다른 회차가 모두 취소되었으면") {
                then("확정한다") {
                    val records =
                        listOf(
                            record(1, PreregisterSessionStatus.CANCELLED),
                            record(2, PreregisterSessionStatus.CANCELLED),
                        )
                    releasesHeldCommit(records, revision = null, sessionId = 1) shouldBe false
                }
            }
            `when`("취소한 회차만 아직 확정 상태로 남아 있으면") {
                then("다른 회차가 아니므로 확정한다") {
                    val records = listOf(record(1, PreregisterSessionStatus.ACTIVE))
                    releasesHeldCommit(records, revision = null, sessionId = 1) shouldBe false
                }
            }
            `when`("다른 회차가 현장 재등록으로 끝났으면") {
                then("확정 상태가 아니므로 확정한다") {
                    val records = listOf(record(2, PreregisterSessionStatus.FIELD_REGISTERED))
                    releasesHeldCommit(records, revision = null, sessionId = 1) shouldBe false
                }
            }
        }
    })
