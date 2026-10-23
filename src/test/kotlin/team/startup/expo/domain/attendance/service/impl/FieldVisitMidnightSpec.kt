package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.service.EntryLogService
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.EntryLogWriter
import team.startup.expo.domain.attendance.support.EntryReplayPolicy
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.attendance.support.PreregisterSessionValidator
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.config.EntryReplayProperties
import team.startup.expo.support.UnitTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class FieldVisitMidnightSpec :
    BehaviorSpec({
        given("KST 자정 직전 시작한 FIELD 스캔") {
            `when`("원격 확인 중 다음날로 넘어가면") {
                then("guard·권위·fence·User·집계 로그가 모두 처음 고정한 날짜를 사용한다") {
                    val day = LocalDate.of(2026, 10, 22)
                    val started = day.atTime(23, 59, 59)
                    var instant = Instant.parse("2026-10-22T14:59:59Z")
                    val clock = mockk<Clock>()
                    every { clock.instant() } answers { instant }
                    every { clock.zone } returns ZoneId.of("Asia/Seoul")
                    val validator = mockk<PreregisterSessionValidator>(relaxed = true)
                    every { validator.check("expo", 7, any()) } answers {
                        instant = Instant.parse("2026-10-22T15:00:01Z")
                        null
                    }
                    every { validator.checkNotCancelled("expo", 7, null, day) } returns null
                    val registration = UUID.randomUUID()
                    every { validator.activeField("expo", 7, day) } returns
                        FieldVisit(registration, "expo", 7, day, 1, FieldVisitStatus.ACTIVE, UUID.randomUUID())
                    val fields = mockk<FieldVisitRepository>(relaxed = true)
                    every { fields.held("expo", 7, null, day.plusDays(1)) } returns true
                    val locks = mockk<CancellationGuardRepository>(relaxed = true)
                    every { locks.findByExpoIdAndParticipantId(any(), any()) } returns null
                    val user = mockk<UserClient>()
                    every { user.recordEntry(any()) } returns RecordEntryResDto(7, "tester", null, true, "STANDARD", null, null)
                    val logs = mockk<EntryLogService>(relaxed = true)
                    val fence = mockk<FieldEntryFence>(relaxed = true)
                    val service =
                        ScanEntryServiceImpl(
                            user,
                            UnitTestSupport.circuitBreaker(),
                            mockk(relaxed = true),
                            validator,
                            CancellationGuardGate(UnitTestSupport.transactionManager(), locks, fields, validator, clock),
                            logs,
                            mockk<EntryLogRepository>(relaxed = true),
                            EntryLogWriter(mockk<EntryLogRepository>(relaxed = true)),
                            EntryReplayPolicy(EntryReplayProperties()),
                            JsonMapper.builder().build(),
                            clock,
                            fence,
                        )
                    service.scan("expo", ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, participantId = 7, code = "fake-code"))
                    verify {
                        fields.held("expo", 7, null, day)
                        fence.begin("expo", 7, day)
                        user.recordEntry(
                            RecordEntryReqDto(
                                "expo",
                                "STANDARD",
                                participantId = 7,
                                code = "fake-code",
                                attendanceDate = day,
                                fieldVisitRegistrationId = registration,
                                fieldVisitGeneration = 1,
                            ),
                        )
                        logs.recordEntry("expo", EntrySubjectType.STANDARD, "7", enteredAt = started)
                    }
                    verify(exactly = 0) { fields.held("expo", 7, null, day.plusDays(1)) }
                }
            }
        }
    })
