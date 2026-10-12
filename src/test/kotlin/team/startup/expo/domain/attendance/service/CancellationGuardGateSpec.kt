package team.startup.expo.domain.attendance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.LocalDateTime

class CancellationGuardGateSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val repository = mockk<CancellationGuardRepository>(relaxed = true)
    private val gate = CancellationGuardGate(UnitTestSupport.transactionManager(), repository)
    private val expoId = "expo-1"

    private fun guard(state: CancellationGuardState) =
        CancellationGuard(
            expoId = expoId,
            participantId = 7L,
            operationId = "op",
            state = state,
            createdAt = LocalDateTime.of(2026, 5, 10, 3, 0),
            updatedAt = LocalDateTime.of(2026, 5, 10, 3, 0),
        )

    init {
        given("취소 보호 관문에서 참가자를 스캔할 때") {
            `when`("보호가 없으면") {
                every { repository.findByExpoIdAndParticipantId(expoId, 7L) } returns null

                val result = gate.scanning(expoId, participantId = 7L) { "기록됨" }

                then("블록을 실행해 결과를 돌려준다") {
                    result shouldBe "기록됨"
                }
                then("락 대기 시간 상한을 둔 뒤 참가자 키로 공유 락을 잡는다") {
                    verifyOrder {
                        repository.setLockTimeout(CancellationGuardGate.LOCK_TIMEOUT)
                        repository.lockShared(CancellationGuardGate.participantKey(expoId, 7L))
                    }
                }
            }

            `when`("보호가 HELD이면") {
                every { repository.findByExpoIdAndParticipantId(expoId, 7L) } returns guard(CancellationGuardState.HELD)
                var ran = false

                then("409로 거절하고 블록을 실행하지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.") {
                        gate.scanning(expoId, participantId = 7L) { ran = true }
                    }
                    ran shouldBe false
                }
            }

            `when`("보호가 COMMITTED이면") {
                every { repository.findByExpoIdAndParticipantId(expoId, 7L) } returns guard(CancellationGuardState.COMMITTED)
                var ran = false

                then("400으로 거절하고 블록을 실행하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                        gate.scanning(expoId, participantId = 7L) { ran = true }
                    }
                    ran shouldBe false
                }
            }

            `when`("블록이 예외를 던지면") {
                every { repository.findByExpoIdAndParticipantId(expoId, 7L) } returns null

                then("그 예외를 그대로 전파한다") {
                    val e =
                        shouldThrow<IllegalStateException> {
                            gate.scanning(expoId, participantId = 7L) { throw IllegalStateException("블록 실패") }
                        }
                    e.message shouldBe "블록 실패"
                }
            }

            `when`("락 대기 시간이 넘어 CannotAcquireLockException이 나면") {
                every { repository.lockShared(any()) } throws CannotAcquireLockException("lock timeout")
                var ran = false

                then("503으로 바꾸고 블록을 실행하지 않는다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "취소 처리와 겹쳐 잠시 후 다시 시도해 주세요.") {
                        gate.scanning(expoId, participantId = 7L) { ran = true }
                    }
                    ran shouldBe false
                }
            }
        }

        given("취소 보호 관문에서 연수자를 스캔할 때") {
            `when`("연수자 키의 보호가 HELD이면") {
                every { repository.findByExpoIdAndTraineeId(expoId, 3L) } returns guard(CancellationGuardState.HELD)

                then("409로 거절한다") {
                    shouldFailWith(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.") {
                        gate.scanning(expoId, traineeId = 3L) { }
                    }
                    verify { repository.lockShared(CancellationGuardGate.traineeKey(expoId, 3L)) }
                }
            }

            `when`("연수자 키의 보호가 COMMITTED이면") {
                every { repository.findByExpoIdAndTraineeId(expoId, 3L) } returns guard(CancellationGuardState.COMMITTED)

                then("400으로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                        gate.scanning(expoId, traineeId = 3L) { }
                    }
                }
            }

            `when`("참가자와 연수자 키를 함께 주고 참가자는 보호가 없지만 연수자 키에 보호가 있으면") {
                every { repository.findByExpoIdAndParticipantId(expoId, 7L) } returns null
                every { repository.findByExpoIdAndTraineeId(expoId, 3L) } returns guard(CancellationGuardState.COMMITTED)

                then("두 키의 공유 락을 모두 잡고 연수자 보호로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") {
                        gate.scanning(expoId, participantId = 7L, traineeId = 3L) { }
                    }
                    verify { repository.lockShared(CancellationGuardGate.participantKey(expoId, 7L)) }
                    verify { repository.lockShared(CancellationGuardGate.traineeKey(expoId, 3L)) }
                }
            }

            `when`("참가자도 연수자도 주지 않으면") {
                val result = gate.scanning(expoId) { 1 }

                then("락도 보호 조회도 없이 블록만 실행한다") {
                    result shouldBe 1
                    verify(exactly = 0) { repository.lockShared(any()) }
                    verify(exactly = 0) { repository.findByExpoIdAndParticipantId(any(), any()) }
                    verify(exactly = 0) { repository.findByExpoIdAndTraineeId(any(), any()) }
                }
            }
        }

        given("락 키를 만들 때") {
            `when`("참가자와 연수자 키를 만들면") {
                then("서로 다른 형식으로 구분된다") {
                    CancellationGuardGate.participantKey("e", 1L) shouldBe "guard:e:p:1"
                    CancellationGuardGate.traineeKey("e", 1L) shouldBe "guard:e:t:1"
                }
            }
        }
    }
}
