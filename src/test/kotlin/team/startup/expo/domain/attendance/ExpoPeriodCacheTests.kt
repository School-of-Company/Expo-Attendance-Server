package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.config.ExpoPeriodCacheProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ExpoPeriodCacheTests {
    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?) = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-10T01:00:00Z"))
    private val client = mock(ExpoClient::class.java)

    private fun validator(ttlSeconds: Long) =
        ExpoPeriodValidator(client, CircuitBreaker.ofDefaults("expo-test"), ExpoPeriodCacheProperties(ttlSeconds), clock)

    private fun notFound() =
        FeignException.NotFound(
            "none",
            Request.create(Request.HttpMethod.GET, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate()),
            null,
            emptyMap(),
        )

    @Test
    fun `기간 안에서는 박람회 서비스를 한 번만 부르고 기간이 지나면 다시 부른다`() {
        doReturn(ExpoPeriodResDto("2026-10-09", "2026-10-11")).`when`(client).getPeriod("expo-1")
        val validator = validator(30)

        validator.period("expo-1") shouldBe (LocalDate.parse("2026-10-09") to LocalDate.parse("2026-10-11"))
        clock.now = clock.now.plusSeconds(29)
        validator.period("expo-1")
        verify(client, times(1)).getPeriod("expo-1")

        clock.now = clock.now.plusSeconds(1)
        validator.period("expo-1")
        verify(client, times(2)).getPeriod("expo-1")
    }

    @Test
    fun `박람회마다 따로 기억한다`() {
        doReturn(ExpoPeriodResDto("2026-10-09", "2026-10-11")).`when`(client).getPeriod("expo-a")
        doReturn(ExpoPeriodResDto("2026-11-01", "2026-11-03")).`when`(client).getPeriod("expo-b")
        val validator = validator(30)

        validator.period("expo-a") shouldBe (LocalDate.parse("2026-10-09") to LocalDate.parse("2026-10-11"))
        validator.period("expo-b") shouldBe (LocalDate.parse("2026-11-01") to LocalDate.parse("2026-11-03"))
    }

    @Test
    fun `없음과 장애 응답은 기억하지 않고 다음 호출에서 다시 부른다`() {
        val validator = validator(30)
        doThrow(notFound()).`when`(client).getPeriod("expo-none")
        assertThrows<ExpectedException> { validator.period("expo-none") }

        doReturn(ExpoPeriodResDto("2026-10-09", "2026-10-11")).`when`(client).getPeriod("expo-none")
        validator.period("expo-none") shouldBe (LocalDate.parse("2026-10-09") to LocalDate.parse("2026-10-11"))
        verify(client, times(2)).getPeriod("expo-none")
    }

    @Test
    fun `기간이 0이면 캐시를 끄고 매번 부른다`() {
        doReturn(ExpoPeriodResDto("2026-10-09", "2026-10-11")).`when`(client).getPeriod("expo-1")
        val validator = validator(0)

        validator.period("expo-1")
        validator.period("expo-1")
        verify(client, times(2)).getPeriod("expo-1")
    }
}
