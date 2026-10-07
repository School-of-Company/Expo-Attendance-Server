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
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate

class QrTokenApiTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var qrTokenRepository: QrTokenRepository

    @Autowired
    lateinit var qrEntryRepository: QrEntryRepository

    @Autowired
    lateinit var entryOutboxRepository: EntryOutboxRepository

    @Test
    fun `토큰을 요청한 개수만큼 22자 난수로 발급하고 저장한다`() {
        val response =
            mockMvc
                .perform(
                    post("/qr-tokens/expo-issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"count": 50}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.tokens.length()").value(50))
                .andReturn()
                .response.contentAsString

        val tokens = Regex("\"([A-Za-z0-9_-]{22})\"").findAll(response).map { it.groupValues[1] }.toList()
        tokens shouldHaveSize 50
        tokens.toSet() shouldHaveSize 50
        qrTokenRepository.findAllById(tokens).map { it.category }.toSet() shouldBe setOf("STANDARD")
    }

    @Test
    fun `개수가 범위를 벗어나면 400이다`() {
        listOf(0, 1001).forEach { count ->
            mockMvc
                .perform(
                    post("/qr-tokens/expo-issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"count": $count}"""),
                ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `첫 스캔은 입장을 기록하고 같은 날 두 번째 스캔은 400이다`() {
        qrTokenRepository.save(QrToken(token = "scan-token-1", expoId = "expo-scan", category = "STANDARD"))

        mockMvc.perform(scan("expo-scan", "scan-token-1")).andExpect(status().isOk)
        mockMvc.perform(scan("expo-scan", "scan-token-1")).andExpect(status().isBadRequest)

        qrEntryRepository.existsByToken("scan-token-1") shouldBe true
    }

    @Test
    fun `없는 토큰과 다른 박람회 토큰은 404이다`() {
        qrTokenRepository.save(QrToken(token = "scan-token-2", expoId = "expo-scan", category = "STANDARD"))

        mockMvc.perform(scan("expo-scan", "unknown-token")).andExpect(status().isNotFound)
        mockMvc.perform(scan("expo-other", "scan-token-2")).andExpect(status().isNotFound)
        qrEntryRepository.existsByToken("scan-token-2") shouldBe false
    }

    @Test
    fun `박람회 정리는 내부 토큰이 있어야 하고 토큰과 입장 기록과 아웃박스를 지운다`() {
        qrTokenRepository.save(QrToken(token = "clean-token", expoId = "expo-clean", category = "STANDARD"))
        qrTokenRepository.save(QrToken(token = "keep-token", expoId = "expo-keep", category = "STANDARD"))
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
}
