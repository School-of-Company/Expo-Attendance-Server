package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.attendance.service.ScannerName

class ScannerNameTests {
    @Test
    fun `앞뒤 공백과 제어 문자를 지우고 40자로 자른다`() {
        ScannerName.of("  입구-1\u0007 ") shouldBe "입구-1"
        ScannerName.of("x".repeat(60)) shouldBe "x".repeat(40)
    }

    @Test
    fun `비었거나 공백뿐이면 기록하지 않는다`() {
        ScannerName.of(null) shouldBe null
        ScannerName.of("") shouldBe null
        ScannerName.of("   ") shouldBe null
    }
}
