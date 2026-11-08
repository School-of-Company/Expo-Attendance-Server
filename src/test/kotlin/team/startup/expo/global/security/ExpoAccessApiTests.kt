package team.startup.expo.global.security

import feign.FeignException
import feign.Request
import feign.Response
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.presentation.AdminDataChangeController
import team.startup.expo.domain.attendance.presentation.AttendanceController
import team.startup.expo.domain.attendance.presentation.EntrySummaryController
import team.startup.expo.domain.attendance.presentation.ProgramAttendanceController
import team.startup.expo.domain.attendance.presentation.WalkInController
import team.startup.expo.domain.attendance.service.EntryListService
import team.startup.expo.domain.attendance.service.EntryStatsService
import team.startup.expo.domain.attendance.service.EntrySummaryService
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.domain.attendance.service.ScanExitService
import team.startup.expo.domain.attendance.service.ScanStandardProgramService
import team.startup.expo.domain.attendance.service.ScanTrainingProgramService
import team.startup.expo.domain.attendance.service.SubscribeAdminDataChangesService
import team.startup.expo.domain.attendance.service.WalkInService
import team.startup.expo.domain.attendance.support.AdminStatsCache
import team.startup.expo.domain.qr.presentation.QrAttendanceController
import team.startup.expo.domain.qr.presentation.QrTokenController
import team.startup.expo.domain.qr.service.GetQrTokenSummaryService
import team.startup.expo.domain.qr.service.GetQrTokensService
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.domain.qr.service.RevokeQrTokenService
import team.startup.expo.domain.qr.service.ScanQrTokenService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.config.AdminStatsCacheProperties
import team.startup.expo.global.exception.GlobalExceptionHandler
import team.startup.expo.global.swagger.SwaggerProperties

@WebMvcTest(
    controllers = [
        AttendanceController::class,
        ProgramAttendanceController::class,
        EntrySummaryController::class,
        QrTokenController::class,
        QrAttendanceController::class,
        WalkInController::class,
        AdminDataChangeController::class,
    ],
    properties = ["internal.token=test-internal-token-0123456789abcdef", "admin-events.enabled=true"],
)
@ContextConfiguration(
    classes = [
        SecurityConfig::class,
        ExpoAccessGuard::class,
        ExpoAccessApiTests.Config::class,
        GlobalExceptionHandler::class,
        AttendanceController::class,
        ProgramAttendanceController::class,
        EntrySummaryController::class,
        QrTokenController::class,
        QrAttendanceController::class,
        WalkInController::class,
        AdminDataChangeController::class,
    ],
)
@EnableConfigurationProperties(InternalProperties::class, SwaggerProperties::class)
class ExpoAccessApiTests {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InternalProperties::class, SwaggerProperties::class)
    class Config {
        @Bean
        fun expoCircuitBreaker(): CircuitBreaker = CircuitBreaker.ofDefaults("expo-access-test")

        @Bean
        fun statsCache(): AdminStatsCache = AdminStatsCache(AdminStatsCacheProperties(ttlMillis = 60_000))
    }

    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var expoCircuitBreaker: CircuitBreaker

    @MockitoBean private lateinit var expoClient: ExpoClient

    @MockitoBean private lateinit var walkIn: WalkInService

    @MockitoBean private lateinit var events: SubscribeAdminDataChangesService

    @MockitoBean private lateinit var qrScan: ScanQrTokenService

    @MockitoBean private lateinit var entry: ScanEntryService

    @MockitoBean private lateinit var exit: ScanExitService

    @MockitoBean private lateinit var standard: ScanStandardProgramService

    @MockitoBean private lateinit var training: ScanTrainingProgramService

    @MockitoBean private lateinit var summary: EntrySummaryService

    @MockitoBean private lateinit var stats: EntryStatsService

    @MockitoBean private lateinit var entries: EntryListService

    @Autowired private lateinit var cache: AdminStatsCache

    @MockitoBean private lateinit var issue: IssueQrTokensService

    @MockitoBean private lateinit var qrSummary: GetQrTokenSummaryService

    @MockitoBean private lateinit var qrList: GetQrTokensService

    @MockitoBean private lateinit var revoke: RevokeQrTokenService

    @BeforeEach
    fun resetCircuit() {
        expoCircuitBreaker.reset()
        cache.invalidateAll()
    }

    @Test
    fun `다른 박람회 스캔은 Expo 판정대로 403이며 입장을 기록하지 않는다`() {
        deny("other-expo", "CHECKIN")
        mvc
            .perform(
                patch("/attendance/other-expo")
                    .header("X-User-Id", "staff:7")
                    .header("X-User-Role", "ROLE_STAFF")
                    .header("Authorization", "Bearer test-staff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":1,"code":"test-code"}"""),
            ).andExpect(status().isForbidden)
        verifyNoInteractions(entry)
    }

    @Test
    fun `프로그램 스캔은 본문의 박람회 권한을 확인한다`() {
        deny("body-expo", "CHECKIN")
        for (kind in listOf("standard", "training")) {
            mvc
                .perform(
                    patch("/attendance/$kind/1")
                        .header("X-User-Id", "staff:7")
                        .header("X-User-Role", "ROLE_STAFF")
                        .header("Authorization", "Bearer test-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"expoId":"body-expo","participantId":1,"code":"test-code"}"""),
                ).andExpect(status().isForbidden)
        }
        verifyNoInteractions(standard, training)
    }

    @Test
    fun `스태프는 개인정보 목록과 종이 QR 발급에 접근하지 못한다`() {
        mvc
            .perform(
                get("/attendance/summary/expo/entries")
                    .header("X-User-Id", "staff:7")
                    .header("X-User-Role", "ROLE_STAFF")
                    .header("Authorization", "Bearer test-staff"),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                post("/qr-tokens/expo")
                    .header("X-User-Id", "staff:7")
                    .header("X-User-Role", "ROLE_STAFF")
                    .header("Authorization", "Bearer test-staff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"category":"ADULT","count":1}"""),
            ).andExpect(status().isForbidden)
        verifyNoInteractions(expoClient, entries, issue)
    }

    @Test
    fun `역할 없는 JWT 사용자는 스캔할 수 없다`() {
        mvc
            .perform(
                patch("/attendance/expo")
                    .header("X-User-Id", "1")
                    .header("Authorization", "Bearer test-no-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":1,"code":"test-code"}"""),
            ).andExpect(status().isForbidden)
        verifyNoInteractions(expoClient, entry)
    }

    @Test
    fun `통계 캐시보다 먼저 권한을 확인한다`() {
        for ((path, permission) in listOf(
            "" to "STATS_READ",
            "/hourly" to "STATS_READ",
            "/recent" to "CHECKIN",
            "/scanners" to "STATS_READ",
        )) {
            deny("expo", permission)
            mvc
                .perform(
                    get("/attendance/summary/expo$path")
                        .header("X-User-Id", "staff:7")
                        .header("X-User-Role", "ROLE_STAFF")
                        .header("Authorization", "Bearer test-staff"),
                ).andExpect(status().isForbidden)
        }
        verifyNoInteractions(summary, stats)
    }

    @Test
    fun `판정 서비스 장애면 스캔을 기록하지 않고 503이다`() {
        doThrow(IllegalStateException("test-unavailable")).`when`(expoClient).requireAdminAccess("expo", "Bearer test-staff", "CHECKIN")
        mvc
            .perform(
                patch("/attendance/expo")
                    .header("X-User-Id", "staff:7")
                    .header("X-User-Role", "ROLE_STAFF")
                    .header("Authorization", "Bearer test-staff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"authority":"ROLE_STANDARD","participantId":1,"code":"test-code"}"""),
            ).andExpect(status().isServiceUnavailable)
        verifyNoInteractions(entry)
    }

    private fun deny(
        expoId: String,
        permission: String,
    ) {
        val request = Request.create(Request.HttpMethod.GET, "http://stub", emptyMap(), null, Charsets.UTF_8, null)
        val response =
            Response
                .builder()
                .request(request)
                .status(403)
                .headers(emptyMap())
                .build()
        doThrow(FeignException.errorStatus("adminAccess", response))
            .`when`(expoClient)
            .requireAdminAccess(expoId, "Bearer test-staff", permission)
    }
}
