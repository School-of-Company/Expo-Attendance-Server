package team.startup.expo.domain.qr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.support.FieldQrCode

class FieldQrCodeTests {
    @Test
    fun `성인과 어린이 코드는 범위 안에서 구분을 정한다`() {
        FieldQrCode.parse("ADT-0001")?.category shouldBe QrCategory.ADULT
        FieldQrCode.parse("ADT-0500")?.category shouldBe QrCategory.ADULT
        FieldQrCode.parse("KID-0001")?.category shouldBe QrCategory.CHILD
        FieldQrCode.parse("KID-0500")?.category shouldBe QrCategory.CHILD
    }

    @Test
    fun `공백과 소문자를 맞춘다`() {
        FieldQrCode.parse(" kid-0042 ")?.code shouldBe "KID-0042"
    }

    @Test
    fun `범위 밖이거나 형식이 다르면 현장 코드가 아니다`() {
        val inputs = listOf("ADT-0501", "ADT-0000", "KID-0000", "KID-0501", "ADT-1", "ADT-00001", "ADT-0001-K7", "CHD-0001", "ADT0001", "")
        inputs.forEach { FieldQrCode.parse(it) shouldBe null }
    }

    @Test
    fun `저장 토큰은 박람회마다 달라 같은 코드가 겹치지 않는다`() {
        val code = FieldQrCode.parse("ADT-0001")!!
        code.token("expo-a") shouldBe "expo-a:ADT-0001"
        code.token("expo-a") shouldBe FieldQrCode.tokenOf("expo-a", "adt-0001")
        (code.token("expo-a") == code.token("expo-b")) shouldBe false
        FieldQrCode.tokenOf("expo-a", "some-issued-token") shouldBe "some-issued-token"
    }
}
