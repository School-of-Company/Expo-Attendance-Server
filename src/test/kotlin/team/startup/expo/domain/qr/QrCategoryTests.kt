package team.startup.expo.domain.qr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.support.IntegrationTestSupport

/** 종이 QR 구분은 폼 서비스의 직업(`Occupation`) 9개 값과 같다. */
class QrCategoryTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var qrTokenRepository: QrTokenRepository

    @Test
    fun `폼 서비스의 직업 9개 값과 같은 구분을 가진다`() {
        QrCategory.entries.map { it.name } shouldBe
            listOf(
                "KINDERGARTEN_STUDENT",
                "ELEMENTARY_STUDENT",
                "MIDDLE_SCHOOL_STUDENT",
                "HIGH_SCHOOL_STUDENT",
                "SCHOOL_STAFF",
                "PRE_SERVICE_TEACHER",
                "PARENT",
                "GENERAL",
                "TEACHER",
            )
    }

    @Test
    fun `유치원생 구분으로 종이 QR을 발급하고 저장한다`() {
        val response =
            mockMvc
                .perform(
                    post("/qr-tokens/55555555-5555-4555-8555-555555555555")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"count": 2, "category": "KINDERGARTEN_STUDENT"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val tokens = Regex("\"([A-Za-z0-9_-]{22})\"").findAll(response).map { it.groupValues[1] }.toList()
        tokens.size shouldBe 2
        qrTokenRepository.findAllById(tokens).map { it.category }.toSet() shouldBe setOf(QrCategory.KINDERGARTEN_STUDENT)
    }
}
