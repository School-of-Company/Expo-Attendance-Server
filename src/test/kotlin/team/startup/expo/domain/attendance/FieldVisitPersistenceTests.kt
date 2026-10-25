package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.impl.FieldVisitServiceImpl
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.user.CancellationStatusResDto
import team.startup.expo.global.client.user.ParticipantEntryStatusResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import team.startup.expo.support.UnitTestSupport
import java.time.LocalDate
import java.util.UUID

class FieldVisitPersistenceTests : IntegrationTestSupport() {
    @Autowired lateinit var transactions: PlatformTransactionManager

    @Autowired lateinit var visits: FieldVisitRepository

    @Autowired lateinit var locks: CancellationGuardRepository

    @Autowired lateinit var deleted: DeletedExpoRepository

    @Autowired lateinit var entries: EntryLogRepository

    @Autowired lateinit var fence: FieldEntryFence

    @Autowired lateinit var standard: StandardProgramAttendanceRepository

    @Autowired lateinit var training: TrainingProgramAttendanceRepository

    @Test
    fun `독립 fence는 스캔 트랜잭션 롤백에도 남는다`() {
        val expo = UUID.randomUUID().toString()
        val date = LocalDate.of(2026, 10, 22)
        TransactionTemplate(transactions).executeWithoutResult { status ->
            fence.begin(expo, 7, date)
            status.setRollbackOnly()
        }
        fence.pending(expo, 7, date) shouldBe true
        fence.entered(expo, 7, date) shouldBe false
        visits.deleteExpo(expo)
    }

    @Test
    fun `실제 SQL projection과 guard는 이전 세대를 보존하고 새 세대를 막지 않는다`() {
        val expo = UUID.randomUUID().toString()
        val registration = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val date = LocalDate.of(2026, 10, 22)
        val user = mockk<UserClient>()
        every { user.getCancellationStatus(any()) } returns CancellationStatusResDto(7, true, true, true, 9)
        every { user.getEntryStatus(any()) } returns ParticipantEntryStatusResDto(7, date, false)
        val service =
            FieldVisitServiceImpl(
                transactions,
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
        service.project(expo, 7, registration, ProjectFieldVisitReqDto(date, 1, FieldVisitStatus.ACTIVE, UUID.randomUUID()))
        service.acquire(expo, 7, registration, operation, FieldVisitCancellationReqDto("fake-phone", date, 1))
        visits.held(expo, 7, null, date) shouldBe true
        visits.held(expo, null, 9, date) shouldBe true
        service.commit(expo, 7, registration, operation, FinishFieldVisitGuardReqDto(date, 1))
        visits.find(registration)?.status shouldBe FieldVisitStatus.CANCELLED
        service.project(expo, 7, registration, ProjectFieldVisitReqDto(date, 2, FieldVisitStatus.ACTIVE, UUID.randomUUID()))
        service.commit(expo, 7, registration, operation, FinishFieldVisitGuardReqDto(date, 1))
        visits.find(registration)?.generation shouldBe 2L
        visits.find(registration)?.status shouldBe FieldVisitStatus.ACTIVE
        visits.held(expo, 7, null, date) shouldBe false
        visits.deleteExpo(expo)
    }
}
