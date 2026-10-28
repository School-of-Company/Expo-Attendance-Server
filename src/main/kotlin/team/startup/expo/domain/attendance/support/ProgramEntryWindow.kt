package team.startup.expo.domain.attendance.support

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 일반 프로그램 입실(첫 스캔)을 받는 시간대: 시작 [leadMinutes]분 전부터 종료 시각까지(#198).
 * 일정은 두 형식으로 온다. Expo 동기 조회는 UTC instant(`2026-10-31T01:30:00Z`)이고, 박람회 프로젝션(#240)은
 * 시간대 없는 한국 시간(`2026-10-31T10:30`, 예전 행은 `2026-10-31 10:30`)이다. 어느 경로로 읽어도 같은 시각으로 비교한다.
 * 일정이 없거나 읽을 수 없으면 막지 않는다. 출석이 일정 데이터 때문에 거절되면 현장이 멈추기 때문이다.
 */
object ProgramEntryWindow {
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    fun allowsEntry(
        startedAt: String?,
        endedAt: String?,
        now: Instant,
        leadMinutes: Long,
    ): Boolean {
        val start = parse(startedAt) ?: return true
        val end = parse(endedAt) ?: return true
        return !now.isBefore(start.minus(Duration.ofMinutes(leadMinutes))) && !now.isAfter(end)
    }

    private fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val text = value.trim().replace(' ', 'T')
        return try {
            OffsetDateTime.parse(text).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(text).atZone(SEOUL).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
