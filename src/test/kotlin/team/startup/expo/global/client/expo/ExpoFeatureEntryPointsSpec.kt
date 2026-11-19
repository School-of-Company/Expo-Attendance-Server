package team.startup.expo.global.client.expo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.presentation.AttendanceController
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ScanExitReqDto
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.domain.attendance.service.ScanExitService
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.presentation.QrAttendanceController
import team.startup.expo.domain.qr.presentation.QrTokenController
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.request.RevokeQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.domain.qr.service.RevokeQrTokenService
import team.startup.expo.domain.qr.service.ScanQrTokenService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.ExpoAccessGuard
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailFeatureDisabled
import team.startup.expo.support.shouldFailWith

/** 입장·종이 QR 진입점이 어떤 기능 키로 신규 쓰기를 막고, 어떤 경로를 OFF여도 그대로 두는지 고정한다. */
class ExpoFeatureEntryPointsSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val expoId = "0192f0c8-0000-7000-8000-000000000001"
    private val expoClient = mockk<ExpoClient>()
    private val policy = ExpoFeaturePolicy(expoClient, UnitTestSupport.circuitBreaker("expo"))
    private val accessGuard = mockk<ExpoAccessGuard>(relaxed = true)
    private val scanEntry = mockk<ScanEntryService>(relaxed = true)
    private val scanExit = mockk<ScanExitService>(relaxed = true)
    private val scanQr = mockk<ScanQrTokenService>(relaxed = true)
    private val issue = mockk<IssueQrTokensService>(relaxed = true)
    private val revoke = mockk<RevokeQrTokenService>(relaxed = true)

    private val attendance = AttendanceController(accessGuard, scanEntry, scanExit, policy)
    private val qrAttendance = QrAttendanceController(accessGuard, scanQr, policy)
    private val qrTokens = QrTokenController(accessGuard, issue, mockk(), mockk(), revoke, policy)

    private val entryReq = ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, participantId = 1L, code = "c")
    private val issueReq = IssueQrTokensReqDto(count = 1, category = QrCategory.SCHOOL_STAFF)
    private val unavailable = "박람회 서비스를 잠시 사용할 수 없습니다."

    private fun features(
        qrAdmission: Boolean,
        paperQr: Boolean,
    ) {
        every { expoClient.getFeatures(expoId) } returns
            ExpoFeaturesResDto(
                listOf(
                    mapOf("key" to "QR_ADMISSION", "configuredEnabled" to qrAdmission, "effectiveEnabled" to qrAdmission),
                    // PAPER_QR의 실효 값은 QR_ADMISSION이 꺼지면 함께 꺼진다(Expo가 계산해 보낸다)
                    mapOf("key" to "PAPER_QR", "configuredEnabled" to paperQr, "effectiveEnabled" to (paperQr && qrAdmission)),
                ),
            )
    }

    init {
        given("모두 ON이면") {
            features(qrAdmission = true, paperQr = true)
            attendance.scanEntry(expoId, entryReq, null)
            qrAttendance.scan(expoId, ScanQrTokenReqDto("token"), null)
            qrTokens.issue(expoId, issueReq)

            then("입장·종이 QR 스캔·발급을 처리하고, 권한 확인이 먼저다") {
                verifyOrder {
                    accessGuard.requireAccess(expoId, "CHECKIN")
                    expoClient.getFeatures(expoId)
                    scanEntry.scan(expoId, entryReq, null)
                }
                verify(exactly = 1) { scanQr.scan(expoId, any(), null) }
                verify(exactly = 1) { issue.issue(expoId, issueReq) }
            }
        }

        given("QR_ADMISSION이 OFF이면") {
            features(qrAdmission = false, paperQr = true)

            then("입장 스캔은 409이고 기록하지 않는다") {
                shouldFailFeatureDisabled(ExpoFeature.QR_ADMISSION) { attendance.scanEntry(expoId, entryReq, null) }
                verify(exactly = 0) { scanEntry.scan(any(), any(), any()) }
            }
            then("종이 QR 스캔도 409다") {
                shouldFailFeatureDisabled(ExpoFeature.QR_ADMISSION) { qrAttendance.scan(expoId, ScanQrTokenReqDto("token"), null) }
                verify(exactly = 0) { scanQr.scan(any(), any(), any()) }
            }
            then("종이 QR 발급은 선행 기능이 꺼져 PAPER_QR 실효 OFF라 409다") {
                shouldFailFeatureDisabled(ExpoFeature.PAPER_QR) { qrTokens.issue(expoId, issueReq) }
                verify(exactly = 0) { issue.issue(any(), any()) }
            }
            then("퇴장 스캔과 토큰 철회는 막지 않고 설정도 읽지 않는다") {
                attendance.scanExit(expoId, ScanExitReqDto(token = "token"), null)
                qrTokens.revoke(expoId, RevokeQrTokenReqDto("ADT-0001-K7"))
                verify(exactly = 1) { scanExit.scan(expoId, any(), null) }
                verify(exactly = 1) { revoke.revoke(expoId, any()) }
                verify(exactly = 0) { expoClient.getFeatures(any()) }
            }
        }

        given("PAPER_QR만 OFF이면") {
            features(qrAdmission = true, paperQr = false)

            then("새 종이 QR 발급은 409다") {
                shouldFailFeatureDisabled(ExpoFeature.PAPER_QR) { qrTokens.issue(expoId, issueReq) }
                verify(exactly = 0) { issue.issue(any(), any()) }
            }
            then("이미 발급한 종이 QR은 계속 입장한다") {
                qrAttendance.scan(expoId, ScanQrTokenReqDto("issued-token"), null)
                verify(exactly = 1) { scanQr.scan(expoId, ScanQrTokenReqDto("issued-token"), null) }
            }
            then("미리 인쇄한 현장 코드의 첫 스캔(등록 포함)도 입장한다") {
                qrAttendance.scan(expoId, ScanQrTokenReqDto("ADT-0001"), null)
                verify(exactly = 1) { scanQr.scan(expoId, ScanQrTokenReqDto("ADT-0001"), null) }
            }
        }

        given("기능 설정을 읽지 못하면") {
            every { expoClient.getFeatures(expoId) } throws UnitTestSupport.feignServerError()

            then("입장·종이 QR 스캔·발급은 허용으로 보지 않고 503이다") {
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { attendance.scanEntry(expoId, entryReq, null) }
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { qrAttendance.scan(expoId, ScanQrTokenReqDto("t"), null) }
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { qrTokens.issue(expoId, issueReq) }
                verify(exactly = 0) { scanEntry.scan(any(), any(), any()) }
                verify(exactly = 0) { scanQr.scan(any(), any(), any()) }
                verify(exactly = 0) { issue.issue(any(), any()) }
                shouldThrow<ExpectedException> { attendance.scanEntry(expoId, entryReq, null) }.code shouldBe null
            }
        }

        given("권한 확인이 실패하면") {
            every { accessGuard.requireAccess(expoId, "CHECKIN") } throws ExpectedException(HttpStatus.FORBIDDEN, "권한이 없습니다.")

            then("기능 설정보다 기존 403이 먼저다") {
                shouldFailWith(HttpStatus.FORBIDDEN, "권한이 없습니다.") { attendance.scanEntry(expoId, entryReq, null) }
                verify(exactly = 0) { expoClient.getFeatures(any()) }
            }
        }
    }
}
