package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import team.startup.expo.global.client.expo.ExpoClientProperties
import team.startup.expo.global.client.user.UserClientProperties
import team.startup.expo.global.security.InternalProperties

class InternalPropertiesTests {
    @Test
    fun `32자 이상의 토큰은 받는다`() {
        InternalProperties("a".repeat(32)).token.length shouldBe 32
        UserClientProperties(internalToken = "a".repeat(32)).internalToken.length shouldBe 32
        ExpoClientProperties(internalToken = "a".repeat(32)).internalToken.length shouldBe 32
    }

    @Test
    fun `짧은 토큰은 기동 시점에 거부하고 값을 노출하지 않는다`() {
        val secret = "short-SECRET-VALUE"

        val messages =
            listOf(
                assertThrows(IllegalArgumentException::class.java) { InternalProperties(secret) },
                assertThrows(IllegalArgumentException::class.java) { UserClientProperties(internalToken = secret) },
                assertThrows(IllegalArgumentException::class.java) { ExpoClientProperties(internalToken = secret) },
            ).map { it.message.orEmpty() }

        messages.any { it.contains(secret) } shouldBe false
    }

    @Test
    fun `toString은 토큰을 가린다`() {
        val token = "a".repeat(32)

        listOf(
            InternalProperties(token).toString(),
            UserClientProperties(internalToken = token).toString(),
            ExpoClientProperties(internalToken = token).toString(),
        ).any { it.contains(token) } shouldBe false
    }
}
