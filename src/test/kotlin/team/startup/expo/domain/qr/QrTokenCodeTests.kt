package team.startup.expo.domain.qr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrTokenCode

class QrTokenCodeTests {
    @Test
    fun `구분 접두사, 네 자리 연번, 확인 문자 두 자리로 만든다`() {
        val code = QrTokenCode.of(QrCategory.ADULT, 1)

        Regex("^ADT-0001-[0-9A-Z]{2}$").matches(code) shouldBe true
        QrTokenCode.of(QrCategory.CHILD, 12).startsWith("CHD-0012-") shouldBe true
        QrTokenCode.of(QrCategory.ADULT, 12345).startsWith("ADT-12345-") shouldBe true
    }

    @Test
    fun `같은 입력은 같은 코드이고 구분 접두사는 서로 다르다`() {
        QrTokenCode.of(QrCategory.ADULT, 7) shouldBe QrTokenCode.of(QrCategory.ADULT, 7)
        QrCategory.entries
            .map { it.codePrefix }
            .toSet()
            .size shouldBe QrCategory.entries.size
    }

    @Test
    fun `공백과 소문자를 맞춰 확인하고 확인 문자가 틀리면 거부한다`() {
        val code = QrTokenCode.of(QrCategory.ADULT, 42)

        QrTokenCode.normalize("  ${code.lowercase()} ") shouldBe code
        // 확인 문자를 다른 값으로 바꾼다
        val wrong = code.dropLast(2) + if (code.takeLast(2) == "00") "01" else "00"
        QrTokenCode.normalize(wrong) shouldBe null
        QrTokenCode.normalize("ADT-1-00") shouldBe null
        QrTokenCode.normalize("abc") shouldBe null
    }

    @Test
    fun `이웃한 연번은 확인 문자가 서로 다르다`() {
        // 연번을 한 칸 잘못 읽어도 확인 문자로 걸러지는지 샘플로 본다
        (1..2000).forEach { serial ->
            val code = QrTokenCode.of(QrCategory.ADULT, serial)
            val typo = QrTokenCode.of(QrCategory.ADULT, serial + 1)
            (code == typo) shouldBe false
        }
    }
}
