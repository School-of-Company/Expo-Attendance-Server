package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.AcquireCancellationGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CommitCancellationGuardReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.CancellationStatusResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.LocalDateTime

class CancellationGuardServiceImplSpec : BehaviorSpec() {
    // 테스트(말단)마다 새 인스턴스를 만들어 목 호출 기록이 섞이지 않게 한다
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)
    private val sessionRepository = mockk<PreregisterSessionRepository>()
    private val standardAttendanceRepository = mockk<StandardProgramAttendanceRepository>()
    private val trainingAttendanceRepository = mockk<TrainingProgramAttendanceRepository>()
    private val deletedExpoRepository = mockk<DeletedExpoRepository>(relaxed = true)
    private val userClient = mockk<UserClient>()
    private val clock = UnitTestSupport.fixedClock()
    private val now = LocalDateTime.now(clock)

    private val service =
        CancellationGuardServiceImpl(
            UnitTestSupport.transactionManager(),
            guardRepository,
            sessionRepository,
            standardAttendanceRepository,
            trainingAttendanceRepository,
            deletedExpoRepository,
            userClient,
            UnitTestSupport.circuitBreaker("user"),
            clock,
        )

    private val expoId = "expo-1"
    private val participantId = 7L
    private val operationId = "op-1"
    private val acquireReq = AcquireCancellationGuardReqDto("010-0000-0000")

    private fun guard(
        operation: String = operationId,
        state: CancellationGuardState = CancellationGuardState.HELD,
    ) = CancellationGuard(
        expoId = expoId,
        participantId = participantId,
        operationId = operation,
        state = state,
        createdAt = now,
        updatedAt = now,
    )

    private fun session(
        status: PreregisterSessionStatus = PreregisterSessionStatus.ACTIVE,
        revision: Long = 0,
        sessionId: Long = 1L,
    ) = PreregisterSession(
        expoId = expoId,
        participantId = participantId,
        sessionId = sessionId,
        status = status,
        revision = revision,
        updatedAt = now,
    )

    private fun userStatus(
        entered: Boolean = false,
        linkedTraineeId: Long? = null,
    ) = CancellationStatusResDto(participantId, true, entered, linkedTraineeId != null, linkedTraineeId)

    private fun givenNoGuard() {
        every { deletedExpoRepository.existsById(expoId) } returns false
        every { guardRepository.existsAborted(expoId, participantId, operationId) } returns false
        every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns null
    }

    private fun givenExistingGuard(existing: CancellationGuard?) {
        every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns existing
    }

    init {
        given("취소 보호를 거는(acquire) 요청에서") {
            `when`("보호가 없고 유저가 입장·출석이 없다고 답하면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus()
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("보호를 HELD로 걸고 Acquired를 돌려준다") {
                    result shouldBe AcquireCancellationGuardResult.Acquired
                    verify(exactly = 1) { guardRepository.insertHeldIfAbsent(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
                then("참가자 단위 배타 락을 잡고 락 대기 시간 상한을 둔다") {
                    verify { guardRepository.setLockTimeout(CancellationGuardGate.LOCK_TIMEOUT) }
                    verify { guardRepository.lockExclusive(CancellationGuardGate.participantKey(expoId, participantId)) }
                }
                then("유저 서비스에는 박람회, 전화번호, 참가자 ID를 보낸다") {
                    verify { userClient.getCancellationStatus(CancellationStatusReqDto(expoId, "010-0000-0000", participantId)) }
                }
            }

            `when`("같은 operationId로 이미 확정(COMMITTED)된 보호가 있으면") {
                givenNoGuard()
                givenExistingGuard(guard(state = CancellationGuardState.COMMITTED))

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("멱등으로 Acquired를 돌려주고 유저 서비스를 부르지 않으며 새로 걸지도 않는다") {
                    result shouldBe AcquireCancellationGuardResult.Acquired
                    verify(exactly = 0) { userClient.getCancellationStatus(any()) }
                    verify(exactly = 0) { guardRepository.insertHeldIfAbsent(any(), any(), any(), any()) }
                }
            }

            `when`("같은 operationId로 아직 HELD인 보호가 있으면") {
                givenNoGuard()
                givenExistingGuard(guard())
                every { userClient.getCancellationStatus(any()) } returns userStatus()
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("다시 만들지 않고 입장 여부를 다시 확인해 Acquired를 돌려준다") {
                    result shouldBe AcquireCancellationGuardResult.Acquired
                    verify(exactly = 0) { guardRepository.insertHeldIfAbsent(any(), any(), any(), any()) }
                    verify(exactly = 1) { userClient.getCancellationStatus(any()) }
                }
            }

            `when`("다른 operationId의 보호가 이미 있으면") {
                givenNoGuard()
                givenExistingGuard(guard(operation = "other-op"))

                then("409를 던지고 보호를 건드리지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "다른 취소 작업이 이미 처리 중이거나 확정되었습니다.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                    verify(exactly = 0) { guardRepository.insertHeldIfAbsent(any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("이미 중단(aborted)된 operationId이면") {
                givenNoGuard()
                every { guardRepository.existsAborted(expoId, participantId, operationId) } returns true

                then("409를 던지고 보호를 다시 걸지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이미 중단된 취소 작업입니다. 새 operationId로 다시 시도해 주세요.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                    verify(exactly = 0) { guardRepository.insertHeldIfAbsent(any(), any(), any(), any()) }
                }
            }

            `when`("삭제된 박람회이면") {
                givenNoGuard()
                every { deletedExpoRepository.existsById(expoId) } returns true

                then("404를 던지고 보호를 걸지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                    verify { deletedExpoRepository.lockExpo(expoId) }
                    verify(exactly = 0) { guardRepository.insertHeldIfAbsent(any(), any(), any(), any()) }
                    verify(exactly = 0) { userClient.getCancellationStatus(any()) }
                }
            }

            `when`("유저 서비스가 404로 답하면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } throws UnitTestSupport.feignNotFound()

                then("보호를 풀고 404를 다시 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("유저 서비스가 5xx로 답하면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } throws UnitTestSupport.feignServerError()

                then("503을 던지고 같은 작업의 재시도를 위해 보호를 풀지 않는다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("유저 서비스가 이미 입장했다고 답하면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus(entered = true, linkedTraineeId = 3L)

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("입장으로 Blocked를 돌려주고 보호를 푼다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = true, programAttended = false)
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
                then("출석 조회와 연수자 키 채우기는 하지 않는다") {
                    verify(exactly = 0) { guardRepository.setTrainee(any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { standardAttendanceRepository.existsByParticipantId(any()) }
                }
            }

            `when`("일반 프로그램 출석이 있으면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus()
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns true

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("출석으로 Blocked를 돌려주고 보호를 푼다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = false, programAttended = true)
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("연결된 연수자가 있고 연수 프로그램 출석이 있으면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus(linkedTraineeId = 3L)
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false
                every { trainingAttendanceRepository.existsByTraineeId(3L) } returns true

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("연수자 키를 채우고 출석으로 Blocked를 돌려주며 보호를 푼다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = false, programAttended = true)
                    verify { guardRepository.lockExclusive(CancellationGuardGate.traineeKey(expoId, 3L)) }
                    verify(exactly = 1) { guardRepository.setTrainee(expoId, participantId, operationId, 3L, now) }
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("연결된 연수자가 있지만 출석이 없으면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus(linkedTraineeId = 3L)
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false
                every { trainingAttendanceRepository.existsByTraineeId(3L) } returns false

                val result = service.acquire(expoId, participantId, operationId, acquireReq)

                then("연수자 키를 채우고 Acquired를 돌려주며 보호는 유지한다") {
                    result shouldBe AcquireCancellationGuardResult.Acquired
                    verify(exactly = 1) { guardRepository.setTrainee(expoId, participantId, operationId, 3L, now) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("연결된 연수자가 없으면") {
                givenNoGuard()
                every { userClient.getCancellationStatus(any()) } returns userStatus()
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false

                service.acquire(expoId, participantId, operationId, acquireReq)

                then("연수 프로그램 출석을 조회하지 않는다") {
                    verify(exactly = 0) { trainingAttendanceRepository.existsByTraineeId(any()) }
                    verify(exactly = 0) { guardRepository.setTrainee(any(), any(), any(), any(), any()) }
                }
            }

            `when`("락 대기 시간이 넘어 CannotAcquireLockException이 나면") {
                every { guardRepository.lockExclusive(any()) } throws CannotAcquireLockException("lock timeout")

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해 주세요.") {
                        service.acquire(expoId, participantId, operationId, acquireReq)
                    }
                }
            }
        }

        given("취소 보호를 확정하는(commit) 요청에서") {
            `when`("이 참가자의 보호가 없으면") {
                givenExistingGuard(null)

                then("409를 던진다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이 작업의 취소 보호가 없습니다. 먼저 보호를 걸어야 합니다.") {
                        service.commit(expoId, participantId, operationId, null)
                    }
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                }
            }

            `when`("다른 operationId의 보호가 있으면") {
                givenExistingGuard(guard(operation = "other-op"))

                then("409를 던지고 그 보호를 확정하지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이 작업의 취소 보호가 없습니다. 먼저 보호를 걸어야 합니다.") {
                        service.commit(expoId, participantId, operationId, null)
                    }
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                }
            }

            `when`("HELD 보호를 revision 없이 확정하면") {
                givenExistingGuard(guard())
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns listOf(session(revision = 9))

                service.commit(expoId, participantId, operationId, null)

                then("revision 없이 COMMITTED로 바꾼다(새 회차가 있어도 밀려나지 않는다)") {
                    verify(exactly = 1) { guardRepository.commit(expoId, participantId, operationId, null, now) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                }
            }

            `when`("HELD 보호를 revision과 함께 확정하고 회차가 더 새롭지 않으면") {
                givenExistingGuard(guard())
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns listOf(session(revision = 4))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 4))

                then("그 revision을 기억하며 COMMITTED로 바꾼다") {
                    verify(exactly = 1) { guardRepository.commit(expoId, participantId, operationId, 4L, now) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("HELD 보호를 revision과 함께 확정하고 저장된 회차가 없으면") {
                givenExistingGuard(guard())
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns emptyList()

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 4))

                then("COMMITTED로 바꾼다") {
                    verify(exactly = 1) { guardRepository.commit(expoId, participantId, operationId, 4L, now) }
                }
            }

            `when`("확정 전에 더 새로운 revision의 ACTIVE 회차가 이미 적용되었으면") {
                givenExistingGuard(guard())
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns listOf(session(revision = 5))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 3))

                then("확정하지 않고 중단 기록을 남긴 뒤 보호를 푼다") {
                    verify(exactly = 1) { guardRepository.insertReleasedByCommit(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                }
            }

            `when`("더 새로운 revision이지만 회차가 CANCELLED이면") {
                givenExistingGuard(guard())
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns
                    listOf(session(status = PreregisterSessionStatus.CANCELLED, revision = 5))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 3))

                then("밀려나지 않은 취소이므로 확정한다") {
                    verify(exactly = 1) { guardRepository.commit(expoId, participantId, operationId, 3L, now) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                }
            }

            `when`("회차 하나만 취소하고 확정 상태인 다른 회차가 남아 있으면") {
                givenExistingGuard(guard())
                every { sessionRepository.cancelExistingSession(expoId, participantId, 1L, 4L, now) } returns 1
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns
                    listOf(session(status = PreregisterSessionStatus.CANCELLED, revision = 4), session(revision = 2, sessionId = 2L))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 4, sessionId = 1L))

                then("그 회차를 취소로 표시하고, 보호는 확정하지 않고 끝내 남은 회차로 입장할 수 있게 한다") {
                    verify(exactly = 1) { sessionRepository.cancelExistingSession(expoId, participantId, 1L, 4L, now) }
                    verify(exactly = 1) { guardRepository.insertReleasedByCommit(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                }
            }

            `when`("해제로 끝난 commit을 같은 operationId로 다시 보내면") {
                givenExistingGuard(null)
                every { guardRepository.existsReleasedByCommit(expoId, participantId, operationId) } returns true

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 4, sessionId = 1L))

                then("오류 없이 끝나고 아무것도 바꾸지 않는다") {
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.insertReleasedByCommit(any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("abort로 끝난 작업을 commit하면") {
                givenExistingGuard(null)
                every { guardRepository.existsAborted(expoId, participantId, operationId) } returns true
                every { guardRepository.existsReleasedByCommit(expoId, participantId, operationId) } returns false

                then("409를 던진다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이 작업의 취소 보호가 없습니다. 먼저 보호를 걸어야 합니다.") {
                        service.commit(expoId, participantId, operationId, null)
                    }
                }
            }

            `when`("회차 하나만 취소하고 남은 확정 회차가 없으면") {
                givenExistingGuard(guard())
                every { sessionRepository.cancelExistingSession(expoId, participantId, 1L, 4L, now) } returns 1
                every { sessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId) } returns
                    listOf(session(status = PreregisterSessionStatus.CANCELLED, revision = 4))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 4, sessionId = 1L))

                then("기존처럼 COMMITTED로 바꾼다") {
                    verify(exactly = 1) { guardRepository.commit(expoId, participantId, operationId, 4L, now) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                }
            }

            `when`("이미 COMMITTED인 같은 작업을 다시 확정하면") {
                givenExistingGuard(guard(state = CancellationGuardState.COMMITTED))

                service.commit(expoId, participantId, operationId, CommitCancellationGuardReqDto(revision = 3))

                then("아무것도 바꾸지 않는다(멱등)") {
                    verify(exactly = 0) { guardRepository.commit(any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("락 대기 시간이 넘으면") {
                every { guardRepository.lockExclusive(any()) } throws CannotAcquireLockException("lock timeout")

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해 주세요.") {
                        service.commit(expoId, participantId, operationId, null)
                    }
                }
            }
        }

        given("취소를 중단하는(abort) 요청에서") {
            `when`("같은 작업이 이미 COMMITTED이면") {
                givenExistingGuard(guard(state = CancellationGuardState.COMMITTED))

                then("409를 던지고 중단 기록도 남기지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이미 확정된 취소 작업은 중단할 수 없습니다.") {
                        service.abort(expoId, participantId, operationId)
                    }
                    verify(exactly = 0) { guardRepository.insertAborted(any(), any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("다른 작업의 COMMITTED 보호만 있으면") {
                givenExistingGuard(guard(operation = "other-op", state = CancellationGuardState.COMMITTED))

                service.abort(expoId, participantId, operationId)

                then("이 작업의 중단 기록만 남기고 다른 작업의 보호는 지우지 않는다") {
                    verify(exactly = 1) { guardRepository.insertAborted(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("아직 acquire가 도착하지 않은 작업이면") {
                givenExistingGuard(null)

                service.abort(expoId, participantId, operationId)

                then("중단 기록(묘비)만 남겨 지연된 acquire를 거절하게 한다") {
                    verify(exactly = 1) { guardRepository.insertAborted(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("같은 작업이 HELD이면") {
                givenExistingGuard(guard())

                service.abort(expoId, participantId, operationId)

                then("중단 기록을 남기고 보호를 푼다") {
                    verify(exactly = 1) { guardRepository.insertAborted(expoId, participantId, operationId, now) }
                    verify(exactly = 0) { guardRepository.insertReleasedByCommit(any(), any(), any(), any()) }
                    verify(exactly = 1) { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }
        }

        given("재신청으로 확정된 보호를 지우는(releaseCommitted) 요청에서") {
            `when`("revision이 있으면") {
                service.releaseCommitted(expoId, participantId, 6L)

                then("revision을 그대로 넘겨 지운다") {
                    verify(exactly = 1) { guardRepository.deleteCommitted(expoId, participantId, 6L) }
                }
            }

            `when`("revision이 없으면") {
                service.releaseCommitted(expoId, participantId, null)

                then("null을 그대로 넘겨 지운다") {
                    verify(exactly = 1) { guardRepository.deleteCommitted(expoId, participantId, null) }
                }
            }

            `when`("락 대기 시간이 넘으면") {
                every { guardRepository.lockExclusive(any()) } throws CannotAcquireLockException("lock timeout")

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해 주세요.") {
                        service.releaseCommitted(expoId, participantId, null)
                    }
                }
            }
        }
    }
}
