package team.startup.expo.domain.qr

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.entity.EntryOutbox
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class QrTokenApiTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var qrTokenRepository: QrTokenRepository

    @Autowired
    lateinit var qrEntryRepository: QrEntryRepository

    @Autowired
    lateinit var entryOutboxRepository: EntryOutboxRepository

    @Autowired
    lateinit var issueQrTokensService: IssueQrTokensService

    @Autowired
    lateinit var deleteExpoDataService: DeleteExpoDataService

    @Test
    fun `토큰을 요청한 개수만큼 22자 난수로 발급하고 저장한다`() {
        val response =
            mockMvc
                .perform(
                    post("/qr-tokens/expo-issue")
                        .asAdmin()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"count": 50, "category": "ELEMENTARY_STUDENT"}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.tokens.length()").value(50))
                .andReturn()
                .response.contentAsString

        val tokens = Regex("\"([A-Za-z0-9_-]{22})\"").findAll(response).map { it.groupValues[1] }.toList()
        tokens shouldHaveSize 50
        tokens.toSet() shouldHaveSize 50
        qrTokenRepository.findAllById(tokens).map { it.category }.toSet() shouldBe setOf(QrCategory.ELEMENTARY_STUDENT)
    }

    @Test
    fun `관리자가 아니면 발급할 수 없다`() {
        val body = """{"count": 5, "category": "GENERAL"}"""

        // 헤더가 없으면 인증되지 않아 401이다
        mockMvc
            .perform(post("/qr-tokens/expo-auth").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)

        // 사용자 ID 없이 역할만 있어도 인증하지 않는다
        mockMvc
            .perform(
                post("/qr-tokens/expo-auth")
                    .header("X-User-Role", "ROLE_ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isUnauthorized)

        // 관리자 외 역할은 403이다
        listOf("ROLE_STANDARD", "ROLE_TRAINEE").forEach { role ->
            mockMvc
                .perform(
                    post("/qr-tokens/expo-auth")
                        .header("X-User-Id", "2")
                        .header("X-User-Role", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isForbidden)
        }
        qrTokenRepository.findAll().none { it.expoId == "expo-auth" } shouldBe true
    }

    @Test
    fun `삭제된 박람회에는 다시 발급할 수 없고 삭제는 여러 번 불러도 같다`() {
        mockMvc
            .perform(
                post("/qr-tokens/expo-gone")
                    .asAdmin()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count": 3, "category": "GENERAL"}"""),
            ).andExpect(status().isCreated)
        repeat(2) {
            mockMvc
                .perform(delete("/internal/expos/expo-gone").header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isNoContent)
        }
        qrTokenRepository.findAll().none { it.expoId == "expo-gone" } shouldBe true

        mockMvc
            .perform(
                post("/qr-tokens/expo-gone")
                    .asAdmin()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count": 3, "category": "GENERAL"}"""),
            ).andExpect(status().isNotFound)
        qrTokenRepository.findAll().none { it.expoId == "expo-gone" } shouldBe true
    }

    @Test
    fun `삭제와 겹친 발급이 있어도 삭제 뒤에는 토큰이 남지 않는다`() {
        val threads = 9
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)

        val tasks =
            (1..threads).map { index ->
                executor.submit {
                    ready.countDown()
                    start.await()
                    runCatching {
                        if (index == 1) {
                            deleteExpoDataService.delete("expo-race")
                        } else {
                            issueQrTokensService.issue("expo-race", IssueQrTokensReqDto(count = 5, category = QrCategory.GENERAL))
                        }
                    }
                }
            }
        ready.await()
        start.countDown()
        tasks.forEach { it.get() }
        executor.shutdown()

        // 발급이 삭제보다 먼저 끝났으면 함께 지워지고, 나중이면 거부되므로 어느 쪽이든 남는 토큰이 없다
        qrTokenRepository.findAll().none { it.expoId == "expo-race" } shouldBe true
    }

    @Test
    fun `개수가 범위를 벗어나면 400이다`() {
        listOf(0, 1001).forEach { count ->
            mockMvc
                .perform(
                    post("/qr-tokens/expo-issue")
                        .asAdmin()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"count": $count, "category": "GENERAL"}"""),
                ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `구분이 없거나 Form에 없는 값이면 400이다`() {
        listOf("""{"count": 5}""", """{"count": 5, "category": "STANDARD"}""").forEach { body ->
            mockMvc
                .perform(
                    post("/qr-tokens/expo-issue")
                        .asAdmin()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `첫 스캔은 입장을 기록하고 같은 날 두 번째 스캔은 400이다`() {
        qrTokenRepository.save(QrToken(token = "scan-token-1", expoId = "expo-scan", category = QrCategory.GENERAL))

        mockMvc.perform(scan("expo-scan", "scan-token-1")).andExpect(status().isOk)
        mockMvc.perform(scan("expo-scan", "scan-token-1")).andExpect(status().isBadRequest)

        qrEntryRepository.existsByToken("scan-token-1") shouldBe true
    }

    @Test
    fun `없는 토큰과 다른 박람회 토큰은 404이다`() {
        qrTokenRepository.save(QrToken(token = "scan-token-2", expoId = "expo-scan", category = QrCategory.GENERAL))

        mockMvc.perform(scan("expo-scan", "unknown-token")).andExpect(status().isNotFound)
        mockMvc.perform(scan("expo-other", "scan-token-2")).andExpect(status().isNotFound)
        qrEntryRepository.existsByToken("scan-token-2") shouldBe false
    }

    @Test
    fun `박람회 정리는 내부 토큰이 있어야 하고 토큰과 입장 기록과 아웃박스를 지운다`() {
        qrTokenRepository.save(QrToken(token = "clean-token", expoId = "expo-clean", category = QrCategory.GENERAL))
        qrTokenRepository.save(QrToken(token = "keep-token", expoId = "expo-keep", category = QrCategory.GENERAL))
        mockMvc.perform(scan("expo-clean", "clean-token")).andExpect(status().isOk)
        entryOutboxRepository.save(
            EntryOutbox(expoId = "expo-clean", participantId = 1, phoneNumber = "01012345678", attendanceDate = LocalDate.now()),
        )

        mockMvc.perform(delete("/internal/expos/expo-clean")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(delete("/internal/expos/expo-clean").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isNoContent)

        qrTokenRepository.existsById("clean-token") shouldBe false
        qrEntryRepository.existsByToken("clean-token") shouldBe false
        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-clean", 1, LocalDate.now()) shouldBe false
        qrTokenRepository.existsById("keep-token") shouldBe true
    }

    private fun scan(
        expoId: String,
        token: String,
    ) = patch("/attendance/qr/$expoId")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"token": "$token"}""")

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.asAdmin() =
        header("X-User-Id", "1").header("X-User-Role", "ROLE_ADMIN")
}
