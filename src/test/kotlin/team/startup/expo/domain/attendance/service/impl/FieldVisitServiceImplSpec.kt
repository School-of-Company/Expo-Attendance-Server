package team.startup.expo.domain.attendance.service.impl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.repository.FieldVisitGuard
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.user.CancellationStatusResDto
import team.startup.expo.global.client.user.ParticipantEntryStatusResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import java.time.LocalDate
import java.util.UUID

class FieldVisitServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val visits = mockk<FieldVisitRepository>()
    private val locks = mockk<CancellationGuardRepository>(relaxed = true)
    private val deleted = mockk<DeletedExpoRepository>(relaxed = true)
    private val entries = mockk<EntryLogRepository>(relaxed = true)
    private val fence = mockk<FieldEntryFence>(relaxed = true)
    private val standard = mockk<StandardProgramAttendanceRepository>(relaxed = true)
    private val training = mockk<TrainingProgramAttendanceRepository>(relaxed = true)
    private val user = mockk<UserClient>()
    private val service =
        FieldVisitServiceImpl(
            UnitTestSupport.transactionManager(),
            visits,
            locks,
            deleted,
            entries,
            fence,
            standard,
            training,
            user,
            UnitTestSupport.circuitBreaker(),
        )
    private val expo = "expo"
    private val participant = 7L
    private val registration = UUID.randomUUID()
    private val operation = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 22)
    private val stored = mutableMapOf<UUID, FieldVisit>()
    private val guards = mutableMapOf<Pair<UUID, Long>, MutableList<FieldVisitGuard>>()
    private val request = FieldVisitCancellationReqDto("01000000000", date, 1)
    private val finish = FinishFieldVisitGuardReqDto(date, 1)

    private fun project(
        generation: Long = 1,
        status: FieldVisitStatus = FieldVisitStatus.ACTIVE,
        op: UUID = UUID.randomUUID(),
    ) {
        service.project(expo, participant, registration, ProjectFieldVisitReqDto(date, generation, status, op))
    }

    private fun acquire(op: UUID = operation) = service.acquire(expo, participant, registration, op, request)

    private fun status() = service.status(expo, participant, registration, request)

    private fun fails(
        status: HttpStatus,
        block: () -> Unit,
    ) {
        shouldThrow<ExpectedException>(block).status shouldBe status
    }

    init {
        every { entries.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(any(), any(), any(), any()) } returns null
        every { visits.find(any()) } answers { stored[firstArg()] }
        every { visits.save(any()) } answers { firstArg<FieldVisit>().let { stored[it.registrationId] = it } }
        every { visits.guards(any(), any()) } answers { guards[firstArg<UUID>() to secondArg<Long>()].orEmpty() }
        every { visits.saveGuard(any(), any(), any(), any()) } answers {
            val visit = firstArg<FieldVisit>()
            val op = secondArg<UUID>()
            val list = guards.getOrPut(visit.registrationId to visit.generation) { mutableListOf() }
            list.removeAll { it.operationId == op }
            list.add(FieldVisitGuard(visit.registrationId, visit.generation, op, thirdArg()))
        }
        every { user.getCancellationStatus(any()) } returns CancellationStatusResDto(participant, true, true, true, 9L)
        every { user.getEntryStatus(any()) } answers {
            ParticipantEntryStatusResDto(participant, date, false)
        }

        given("날짜별 projection") {
            `when`("새 방문일을 등록하면") {
                then("다른 등록을 덮지 않고 ID와 날짜를 저장한다") {
                    project()
                    stored[registration]?.visitDate shouldBe date
                    verify(exactly = 0) { user.recordEntry(any()) }
                }
            }
            `when`("같은 세대를 취소한 뒤 ACTIVE가 늦으면") {
                then("CANCELLED를 유지한다") {
                    project(status = FieldVisitStatus.CANCELLED)
                    project()
                    stored[registration]?.status shouldBe FieldVisitStatus.CANCELLED
                }
            }
            `when`("취소 뒤 새 세대를 등록하고 옛 취소가 늦으면") {
                then("새 세대와 기존 QR 식별을 보존한다") {
                    project(status = FieldVisitStatus.CANCELLED)
                    project(2)
                    project(1, FieldVisitStatus.CANCELLED)
                    stored[registration]?.generation shouldBe 2
                    stored[registration]?.status shouldBe FieldVisitStatus.ACTIVE
                }
            }
            `when`("등록 ID를 다른 날짜나 사람에 재사용하면") {
                then("409이고 기존 projection은 유지한다") {
                    project()
                    fails(HttpStatus.CONFLICT) {
                        service.project(
                            expo,
                            participant,
                            registration,
                            ProjectFieldVisitReqDto(date.plusDays(1), 2, FieldVisitStatus.ACTIVE, operation),
                        )
                    }
                    fails(HttpStatus.CONFLICT) {
                        service.project(expo, 8, registration, ProjectFieldVisitReqDto(date, 2, FieldVisitStatus.ACTIVE, operation))
                    }
                    stored[registration]?.generation shouldBe 1
                }
            }
            `when`("박람회가 삭제되었으면") {
                then("projection을 되살리지 않는다") {
                    every { deleted.existsById(expo) } returns true
                    fails(HttpStatus.NOT_FOUND) { project() }
                    stored.isEmpty() shouldBe true
                }
            }
        }
        given("방문일별 첫입장 권위") {
            project()
            `when`("User 누적 entered만 true이고 대상 날짜 입장은 없으면") {
                then("전날 이력으로 오늘 취소를 막지 않고 정확한 대상과 NONE을 반환한다") {
                    val result = status()
                    result.enteredOnVisitDate shouldBe false
                    result.guardState shouldBe FieldVisitGuardState.NONE
                    result.registrationId shouldBe registration
                    result.participantId shouldBe participant
                    result.visitDate shouldBe date
                    result.generation shouldBe 1
                    acquire()
                    status().guardState shouldBe FieldVisitGuardState.HELD
                }
            }
            `when`("User 대상 날짜 첫입장이 있으면") {
                then("취소를 거절한다") {
                    every { user.getEntryStatus(any()) } returns ParticipantEntryStatusResDto(participant, date, true)
                    fails(HttpStatus.CONFLICT) { acquire() }
                    guards.isEmpty() shouldBe true
                }
            }
            `when`("대상 날짜 입장 후 퇴장했으면") {
                then("여전히 취소를 거절한다") {
                    every { entries.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(any(), any(), any(), any()) } returns
                        EntryLog(
                            expoId = expo,
                            subjectType = EntrySubjectType.STANDARD,
                            subjectId = "7",
                            attendanceDate = date,
                            enteredAt = date.atTime(9, 0),
                            exitedAt = date.atTime(10, 0),
                        )
                    fails(HttpStatus.CONFLICT) { acquire() }
                }
            }
            `when`("원격 응답유실로 PENDING이고 status false이면") {
                then("false를 취소 허가로 바꾸지 않고 503으로 차단한다") {
                    every { fence.pending(expo, participant, date) } returns true
                    fails(HttpStatus.SERVICE_UNAVAILABLE) { acquire() }
                    fails(HttpStatus.SERVICE_UNAVAILABLE) { status() }
                    guards.isEmpty() shouldBe true
                }
            }
            `when`("PENDING 뒤 늦은 User 커밋이 확인되면") {
                then("첫입장을 반환하고 취소는 거절한다") {
                    every { fence.pending(expo, participant, date) } returns true
                    every { user.getEntryStatus(any()) } returns ParticipantEntryStatusResDto(participant, date, true)
                    status().enteredOnVisitDate shouldBe true
                    fails(HttpStatus.CONFLICT) { acquire() }
                }
            }
            `when`("첫입장 fence만 남았으면") {
                then("best-effort 로그 누락과 무관하게 취소를 막는다") {
                    every { fence.entered(expo, participant, date) } returns true
                    fails(HttpStatus.CONFLICT) { acquire() }
                }
            }
            listOf("wrong date", "wrong participant", "404", "5xx").forEach { failure ->
                `when`("User entry-status가 $failure 이면") {
                    then("503으로 차단한다") {
                        when (failure) {
                            "wrong date" -> {
                                every { user.getEntryStatus(any()) } returns
                                    ParticipantEntryStatusResDto(participant, date.minusDays(1), false)
                            }

                            "wrong participant" -> {
                                every { user.getEntryStatus(any()) } returns
                                    ParticipantEntryStatusResDto(99, date, false)
                            }

                            "404" -> {
                                every { user.getEntryStatus(any()) } throws UnitTestSupport.feignNotFound()
                            }

                            else -> {
                                every { user.getEntryStatus(any()) } throws UnitTestSupport.feignServerError()
                            }
                        }
                        fails(HttpStatus.SERVICE_UNAVAILABLE) { acquire() }
                    }
                }
            }
            `when`("대표자 소유확인이 실패하면") {
                then("404이고 보호를 만들지 않는다") {
                    every { user.getCancellationStatus(any()) } throws UnitTestSupport.feignNotFound()
                    fails(HttpStatus.NOT_FOUND) { acquire() }
                    guards.isEmpty() shouldBe true
                }
            }
            `when`("대상 날짜 프로그램 또는 연결 연수자 출석이 있으면") {
                then("관련 출석으로 거절한다") {
                    every { training.existsByTraineeIdAndAttendanceDate(9, date) } returns true
                    status().programAttended shouldBe true
                    fails(HttpStatus.CONFLICT) { acquire() }
                }
            }
        }
        given("날짜별 guard") {
            project()
            `when`("acquire를 재시도하고 commit을 재시도하면") {
                then("한 번의 보호와 취소만 남긴다") {
                    acquire()
                    acquire()
                    service.commit(expo, participant, registration, operation, finish)
                    service.commit(expo, participant, registration, operation, finish)
                    stored[registration]?.status shouldBe FieldVisitStatus.CANCELLED
                    status().guardState shouldBe FieldVisitGuardState.COMMITTED
                    guards[registration to 1L]?.size shouldBe 1
                }
            }
            `when`("abort가 acquire보다 먼저 오면") {
                then("tombstone이 늦은 acquire와 commit을 거절한다") {
                    service.abort(expo, participant, registration, operation, finish)
                    service.abort(expo, participant, registration, operation, finish)
                    fails(HttpStatus.CONFLICT) { acquire() }
                    fails(HttpStatus.CONFLICT) { service.commit(expo, participant, registration, operation, finish) }
                    status().guardState shouldBe FieldVisitGuardState.NONE
                }
            }
            `when`("다른 작업이 HELD이면") {
                then("두 번째 acquire는 충돌한다") {
                    acquire()
                    fails(HttpStatus.CONFLICT) { acquire(UUID.randomUUID()) }
                }
            }
            `when`("옛 HELD 뒤 새 generation을 등록하면") {
                then("옛 acquire commit abort 모두 새 등록을 바꾸지 않는다") {
                    acquire()
                    project(2)
                    acquire()
                    service.commit(expo, participant, registration, operation, finish)
                    service.abort(expo, participant, registration, operation, finish)
                    stored[registration]?.generation shouldBe 2
                    stored[registration]?.status shouldBe FieldVisitStatus.ACTIVE
                }
            }
            `when`("보호를 획득하면") {
                then("참가자 다음 linked trainee 배타락 뒤에 날짜별 출석을 확인한다") {
                    acquire()
                    verifyOrder {
                        deleted.lockExpo(expo)
                        locks.lockExclusive(CancellationGuardGate.participantKey(expo, participant))
                        locks.lockExclusive(CancellationGuardGate.traineeKey(expo, 9))
                        training.existsByTraineeIdAndAttendanceDate(9, date)
                        visits.saveGuard(any(), operation, FieldVisitGuardState.HELD, 9)
                    }
                }
            }
            `when`("한 방문일만 commit하면") {
                then("다른 날짜 FIELD를 보존하고 PRE에는 쓰지 않는다") {
                    val other = UUID.randomUUID()
                    service.project(
                        expo,
                        participant,
                        other,
                        ProjectFieldVisitReqDto(date.plusDays(1), 1, FieldVisitStatus.ACTIVE, UUID.randomUUID()),
                    )
                    acquire()
                    service.commit(expo, participant, registration, operation, finish)
                    stored[other]?.status shouldBe FieldVisitStatus.ACTIVE
                    fails(HttpStatus.CONFLICT) { service.abort(expo, participant, registration, operation, finish) }
                }
            }
        }
    }
}
