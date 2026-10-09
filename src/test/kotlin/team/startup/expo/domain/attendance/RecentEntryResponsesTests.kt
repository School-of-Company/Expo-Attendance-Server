package team.startup.expo.domain.attendance

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.RecentEntryResponses
import team.startup.expo.global.config.EntryRetryProperties
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RecentEntryResponsesTests {
    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-09T01:00:00Z"))
    private val recent = RecentEntryResponses(EntryRetryProperties(windowSeconds = 300), clock)
    private val request = ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, participantId = 7, code = "codeAAAAAAAAAAAAAAAAAA")
    private val response = ScanEntryResDto(7, "홍길동", null, true, "STANDARD", null)

    @Test
    fun `같은 스캐너 사용자가 같은 QR을 기간 안에 다시 찍으면 처음 응답을 돌려준다`() {
        recent.remember("expo-1", request, "staff-1", response)

        clock.now = clock.now.plusSeconds(299)
        recent.find("expo-1", request, "staff-1") shouldBe response
    }

    @Test
    fun `기간이 지나면 기억을 지운다`() {
        recent.remember("expo-1", request, "staff-1", response)

        clock.now = clock.now.plusSeconds(300)
        recent.find("expo-1", request, "staff-1") shouldBe null
    }

    @Test
    fun `다른 스캐너 사용자, 다른 박람회, 다른 코드, 다른 참가자는 돌려주지 않는다`() {
        recent.remember("expo-1", request, "staff-1", response)

        recent.find("expo-1", request, "staff-2") shouldBe null
        recent.find("expo-2", request, "staff-1") shouldBe null
        recent.find("expo-1", request.copy(code = "codeBBBBBBBBBBBBBBBBBB"), "staff-1") shouldBe null
        recent.find("expo-1", request.copy(participantId = 8), "staff-1") shouldBe null
    }

    @Test
    fun `스캐너 사용자를 모르면 기억하지도 돌려주지도 않는다`() {
        recent.remember("expo-1", request, null, response)
        recent.remember("expo-1", request, " ", response)

        recent.find("expo-1", request, null) shouldBe null
        recent.find("expo-1", request, "staff-1") shouldBe null
    }

    @Test
    fun `기간이 0이면 꺼진다`() {
        val disabled = RecentEntryResponses(EntryRetryProperties(windowSeconds = 0), clock)
        disabled.remember("expo-1", request, "staff-1", response)

        disabled.find("expo-1", request, "staff-1") shouldBe null
    }
}
