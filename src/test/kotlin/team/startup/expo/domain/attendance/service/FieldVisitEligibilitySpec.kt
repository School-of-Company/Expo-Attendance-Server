package team.startup.expo.domain.attendance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterEntryProperties
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class FieldVisitEligibilitySpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val field = mockk<FieldVisitRepository>(relaxed = true)
    private val sessions = mockk<PreregisterSessionRepository>()
    private val expo = mockk<ExpoClient>()
    private val day = LocalDate.of(2026, 10, 22)
    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), zone)

    private fun validator(clock: Clock = this.clock) =
        PreregisterSessionValidator(sessions, expo, UnitTestSupport.circuitBreaker(), PreregisterEntryProperties(), clock, field)

    private fun visit(
        date: LocalDate = day,
        status: FieldVisitStatus = FieldVisitStatus.ACTIVE,
    ) = FieldVisit(UUID.randomUUID(), "expo", 7, date, 1, status, UUID.randomUUID())

    private fun pre(status: PreregisterSessionStatus = PreregisterSessionStatus.ACTIVE) =
        PreregisterSession(expoId = "expo", participantId = 7, sessionId = 11, status = status, updatedAt = LocalDateTime.now(clock))

    init {
        every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns emptyList()
        given("오늘 FIELD와 독립 PRE") {
            `when`("오늘 PRE 전화번호 입장에 시간이 다른 ACTIVE 회차가 여러 개면") {
                every { field.visits("expo", 7) } returns emptyList()
                every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns
                    listOf(
                        pre(),
                        PreregisterSession(
                            expoId = "expo",
                            participantId = 7,
                            sessionId = 12,
                            status = PreregisterSessionStatus.ACTIVE,
                            updatedAt = LocalDateTime.now(clock),
                        ),
                    )
                every { expo.getPreregisterSession("expo", 11) } returns
                    PreregisterSessionResDto(11, clock.instant().plusSeconds(3600), clock.instant().plusSeconds(7200))
                every { expo.getPreregisterSession("expo", 12) } returns
                    PreregisterSessionResDto(12, clock.instant().minusSeconds(3600), clock.instant().minusSeconds(60))
                then("이미 끝난 회차도 시간으로 거절하지 않고 earliest-start ID를 전달한다") {
                    validator().checkNotCancelled("expo", 7) shouldBe 12L
                }
            }
            `when`("PRE가 내일이고 오늘 FIELD가 ACTIVE이면") {
                every { field.visits("expo", 7) } returns listOf(visit())
                every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns listOf(pre())
                then("PRE 시간조회나 변경 없이 FIELD로 입장한다") {
                    validator().check("expo", 7) { } shouldBe null
                    validator().checkNotCancelled("expo", 7) shouldBe null
                    validator().isCancelled("expo", 7) shouldBe false
                    verify(exactly = 0) { expo.getPreregisterSession(any(), any()) }
                }
            }
            `when`("FIELD를 취소했지만 오늘 PRE가 유효하면") {
                every { field.visits("expo", 7) } returns listOf(visit(status = FieldVisitStatus.CANCELLED))
                every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns listOf(pre())
                every { expo.getPreregisterSession("expo", 11) } returns
                    PreregisterSessionResDto(11, clock.instant().minusSeconds(60), clock.instant().plusSeconds(60))
                then("PRE 회차와 QR 권한을 유지한다") {
                    validator().check("expo", 7) { } shouldBe 11L
                    validator().checkNotCancelled("expo", 7, 11) shouldBe 11L
                    validator().isCancelled("expo", 7) shouldBe false
                }
            }
            listOf("yesterday FIELD", "tomorrow FIELD", "cancelled FIELD", "legacy unknown").forEach { kind ->
                `when`("$kind 만 있으면") {
                    val records =
                        when (kind) {
                            "yesterday FIELD" -> listOf(visit(day.minusDays(1)))
                            "tomorrow FIELD" -> listOf(visit(day.plusDays(1)))
                            "cancelled FIELD" -> listOf(visit(status = FieldVisitStatus.CANCELLED))
                            else -> emptyList()
                        }
                    every { field.visits("expo", 7) } returns records
                    every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns
                        listOf(pre(PreregisterSessionStatus.FIELD_REGISTERED))
                    then("오늘 입장과 QR을 거절한다") {
                        shouldThrow<ExpectedException> { validator().checkToday("expo", 7) }.status shouldBe HttpStatus.BAD_REQUEST
                        validator().isCancelled("expo", 7) shouldBe true
                    }
                }
            }
            `when`("다른 날짜 PRE만 있으면") {
                every { field.visits("expo", 7) } returns emptyList()
                every { sessions.findAllByExpoIdAndParticipantId("expo", 7) } returns listOf(pre())
                every { expo.getPreregisterSession("expo", 11) } returns
                    PreregisterSessionResDto(11, clock.instant().plusSeconds(86400), clock.instant().plusSeconds(90000))
                then("전화번호와 프로그램도 오늘 권한으로 쓰지 않는다") {
                    shouldThrow<ExpectedException> { validator().checkToday("expo", 7) }.status shouldBe HttpStatus.BAD_REQUEST
                    shouldThrow<ExpectedException> { validator().checkNotCancelled("expo", 7) }.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
        given("KST 자정") {
            val before = Clock.fixed(Instant.parse("2026-10-22T14:59:59Z"), zone)
            val after = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), zone)
            `when`("오늘 등록만 있고 자정이 지나면") {
                every { field.visits("expo", 7) } returns listOf(visit())
                then("전날 FIELD는 다음날 권한이 아니다") {
                    validator(before).activeField("expo", 7)?.visitDate shouldBe day
                    validator(after).activeField("expo", 7) shouldBe null
                    shouldThrow<ExpectedException> { validator(after).checkToday("expo", 7) }.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
            `when`("양일 등록이 있으면") {
                every { field.visits("expo", 7) } returns listOf(visit(), visit(day.plusDays(1)))
                then("자정 뒤 새 방문일의 기존 ID와 QR을 사용한다") {
                    validator(before).activeField("expo", 7)?.visitDate shouldBe day
                    validator(after).activeField("expo", 7)?.visitDate shouldBe day.plusDays(1)
                }
            }
        }
        given("날짜별 HELD 보호") {
            `when`("오늘 참가자 또는 연결 연수자에게 보호가 있으면") {
                val locks = mockk<CancellationGuardRepository>(relaxed = true)
                every { locks.findByExpoIdAndParticipantId(any(), any()) } returns null
                every { locks.findByExpoIdAndTraineeId(any(), any()) } returns null
                every { field.held("expo", any(), any(), day) } returns true
                val gate = CancellationGuardGate(UnitTestSupport.transactionManager(), locks, field, validator(), clock)
                then("기록 전에 두 스캔을 모두 차단한다") {
                    var ran = false
                    shouldThrow<ExpectedException> { gate.scanning("expo", participantId = 7) { ran = true } }.status shouldBe
                        HttpStatus.CONFLICT
                    shouldThrow<ExpectedException> { gate.scanning("expo", traineeId = 9) { ran = true } }.status shouldBe
                        HttpStatus.CONFLICT
                    ran shouldBe false
                }
            }
        }
    }
}
