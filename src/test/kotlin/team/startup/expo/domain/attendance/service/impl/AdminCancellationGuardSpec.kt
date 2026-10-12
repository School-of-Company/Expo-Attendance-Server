package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.user.AdminCancellationStatusReqDto
import team.startup.expo.global.client.user.AdminCancellationStatusResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.LocalDateTime

/** DB 없이 도는 단위 테스트: 관리자 취소용 acquire(#116)는 소유 확인 없이 같은 보호 의미를 지킨다. */
class AdminCancellationGuardSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)
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
            mockk<PreregisterSessionRepository>(),
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
    private val statusReq = AdminCancellationStatusReqDto(expoId, participantId)

    private fun status(
        entered: Boolean = false,
        linkedTraineeId: Long? = null,
    ) = AdminCancellationStatusResDto(participantId, entered, linkedTraineeId != null, linkedTraineeId)

    private fun givenNoGuard() {
        every { deletedExpoRepository.existsById(expoId) } returns false
        every { guardRepository.existsAborted(expoId, participantId, operationId) } returns false
        every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns null
        every { standardAttendanceRepository.existsByParticipantId(participantId) } returns false
        every { trainingAttendanceRepository.existsByTraineeId(any()) } returns false
    }

    init {
        given("관리자 취소 보호를 거는(acquireForAdmin) 요청에서") {
            `when`("보호가 없고 입장·출석이 없으면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } returns status()

                val result = service.acquireForAdmin(expoId, participantId, operationId)

                then("전화번호 없는 관리자용 조회로 상태를 읽고 보호를 HELD로 건다") {
                    result shouldBe AcquireCancellationGuardResult.Acquired
                    verify(exactly = 1) { userClient.getAdminCancellationStatus(statusReq) }
                    verify(exactly = 0) { userClient.getCancellationStatus(any()) }
                    verify(exactly = 1) { guardRepository.insertHeldIfAbsent(expoId, participantId, operationId, now) }
                }
            }

            `when`("이미 입장했다고 유저가 답하면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } returns status(entered = true)

                val result = service.acquireForAdmin(expoId, participantId, operationId)

                then("Blocked(entered)를 돌려주고 건 보호를 푼다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = true, programAttended = false)
                    verify { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("프로그램 출석 기록이 있으면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } returns status()
                every { standardAttendanceRepository.existsByParticipantId(participantId) } returns true

                val result = service.acquireForAdmin(expoId, participantId, operationId)

                then("Blocked(programAttended)를 돌려주고 보호를 푼다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = false, programAttended = true)
                    verify { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("연결된 연수자가 있고 그 연수자에게 연수 출석이 있으면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } returns status(linkedTraineeId = 55L)
                every { trainingAttendanceRepository.existsByTraineeId(55L) } returns true

                val result = service.acquireForAdmin(expoId, participantId, operationId)

                then("연수자 키를 채우고 Blocked(programAttended)를 돌려준다") {
                    result shouldBe AcquireCancellationGuardResult.Blocked(entered = false, programAttended = true)
                    verify { guardRepository.setTrainee(expoId, participantId, operationId, 55L, now) }
                }
            }

            `when`("유저가 모르는 참가자라고(404) 답하면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } throws UnitTestSupport.feignNotFound()

                then("404이고 건 보호를 푼다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.") { service.acquireForAdmin(expoId, participantId, operationId) }
                    verify { guardRepository.deleteHeld(expoId, participantId, operationId) }
                }
            }

            `when`("유저 서비스가 장애면") {
                givenNoGuard()
                every { userClient.getAdminCancellationStatus(statusReq) } throws UnitTestSupport.feignServerError()

                then("503이고 보호를 둔 채 같은 operationId의 재시도를 기다린다(실패 폐쇄)") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.") {
                        service.acquireForAdmin(expoId, participantId, operationId)
                    }
                    verify(exactly = 0) { guardRepository.deleteHeld(any(), any(), any()) }
                }
            }

            `when`("이미 중단된 operationId면") {
                givenNoGuard()
                every { guardRepository.existsAborted(expoId, participantId, operationId) } returns true

                then("유저를 부르지 않고 409로 거절한다") {
                    shouldFailWith(HttpStatus.CONFLICT, "이미 중단된 취소 작업입니다. 새 operationId로 다시 시도해 주세요.") {
                        service.acquireForAdmin(expoId, participantId, operationId)
                    }
                    verify(exactly = 0) { userClient.getAdminCancellationStatus(any()) }
                }
            }

            `when`("다른 취소 작업이 이미 보호를 걸고 있으면") {
                givenNoGuard()
                every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns
                    CancellationGuard(
                        expoId = expoId,
                        participantId = participantId,
                        operationId = "other-op",
                        state = CancellationGuardState.HELD,
                        createdAt = now,
                        updatedAt = now,
                    )

                then("409로 거절하고 유저를 부르지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "다른 취소 작업이 이미 처리 중이거나 확정되었습니다.") {
                        service.acquireForAdmin(expoId, participantId, operationId)
                    }
                    verify(exactly = 0) { userClient.getAdminCancellationStatus(any()) }
                }
            }
        }
    }
}
