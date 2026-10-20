package team.startup.expo.domain.qr.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntryStatus
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryReplayPolicy
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.global.config.EntryReplayProperties
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.LocalDate
import java.time.LocalDateTime

class ScanQrTokenServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val qrTokenRepository = mockk<QrTokenRepository>()
    private val qrEntryRepository = mockk<QrEntryRepository>()
    private val entryLogRepository = mockk<EntryLogRepository>()
    private val expoPeriodValidator = mockk<ExpoPeriodValidator>()
    private val clock = UnitTestSupport.fixedClock()
    private val now = LocalDateTime.now(clock)
    private val today = LocalDate.now(clock)

    private val service =
        ScanQrTokenServiceImpl(
            qrTokenRepository,
            qrEntryRepository,
            entryLogRepository,
            expoPeriodValidator,
            EntryReplayPolicy(EntryReplayProperties(windowSeconds = 30)),
            clock,
        )

    private val expoId = "expo-1"
    private val req = ScanQrTokenReqDto("token-1")

    private fun log(
        enteredAt: LocalDateTime = now.minusSeconds(10),
        exitedAt: LocalDateTime? = null,
        scanner: String? = "door-1",
    ) = EntryLog(
        expoId = expoId,
        subjectType = EntrySubjectType.PAPER_QR,
        subjectId = "token-1",
        attendanceDate = today,
        enteredAt = enteredAt,
        exitedAt = exitedAt,
        scanner = scanner,
    )

    init {
        every { expoPeriodValidator.checkInProgress(expoId, today) } just Runs
        // 오늘 이미 입장했고 퇴장하지 않은 상태
        every { qrEntryRepository.recordEntry(any(), any(), any(), any(), any(), any()) } returns 0
        every { qrEntryRepository.reenter(any(), any(), any(), any(), any()) } returns 0
        every { qrTokenRepository.existsByTokenAndExpoId("token-1", expoId) } returns true
        every { qrTokenRepository.existsByTokenAndExpoIdAndRevokedAtIsNotNull("token-1", expoId) } returns false

        given("종이 QR이 퇴장 없이 이미 입장했을 때(#176)") {
            `when`("입장 후 10초 안에 같은 스캐너로 다시 찍으면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.PAPER_QR,
                        "token-1",
                        today,
                    )
                } returns log()

                then("FIRST와 최초 입장 시각을 응답하고 재입장을 기록하지 않는다") {
                    val res = service.scan(expoId, req, "door-1")
                    res.entryStatus shouldBe EntryStatus.FIRST
                    res.firstEnteredTime shouldBe "02:59"
                    verify(exactly = 1) { qrEntryRepository.recordEntry(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("재생 구간을 넘겨 찍으면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.PAPER_QR,
                        "token-1",
                        today,
                    )
                } returns log(enteredAt = now.minusSeconds(31))

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "오늘 이미 입장한 QR입니다.") { service.scan(expoId, req, "door-1") }
                }
            }

            `when`("다른 스캐너로 찍으면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.PAPER_QR,
                        "token-1",
                        today,
                    )
                } returns log()

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "오늘 이미 입장한 QR입니다.") { service.scan(expoId, req, "door-2") }
                }
            }

            `when`("토큰이 폐기되었으면") {
                every { qrTokenRepository.existsByTokenAndExpoIdAndRevokedAtIsNotNull("token-1", expoId) } returns true
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.PAPER_QR,
                        "token-1",
                        today,
                    )
                } returns log()

                then("재생하지 않고 폐기 오류를 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "사용이 중지된 QR입니다.") { service.scan(expoId, req, "door-1") }
                }
            }
        }

        given("종이 QR이 퇴장한 뒤 다시 찍혔을 때") {
            every { qrEntryRepository.reenter(any(), any(), any(), any(), any()) } returns 1
            every {
                entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                    expoId,
                    EntrySubjectType.PAPER_QR,
                    "token-1",
                    today,
                )
            } returns log(exitedAt = null)

            `when`("스캔하면") {
                then("재생이 아니라 재입장이다") {
                    service.scan(expoId, req, "door-1").entryStatus shouldBe EntryStatus.RE_ENTRY
                }
            }
        }
    }
}
