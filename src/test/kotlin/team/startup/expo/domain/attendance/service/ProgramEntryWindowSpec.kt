package team.startup.expo.domain.attendance.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ProgramEntryWindowSpec : BehaviorSpec() {
    // 한국 시간 10:30~11:30이다(UTC 01:30~02:30)
    private val started = "2026-10-31T10:30"
    private val ended = "2026-10-31T11:30"

    private fun allowed(
        utc: String,
        startedAt: String? = started,
        endedAt: String? = ended,
    ) = ProgramEntryWindow.allowsEntry(startedAt, endedAt, Instant.parse(utc), 10)

    init {
        given("프로그램 입실 시간대에서") {
            `when`("시작 10분 전보다 이르면") {
                then("허용하지 않는다") { allowed("2026-10-31T01:19:59Z") shouldBe false }
            }
            `when`("시작 10분 전부터 종료 시각까지이면") {
                then("허용한다") {
                    allowed("2026-10-31T01:20:00Z") shouldBe true
                    allowed("2026-10-31T02:00:00Z") shouldBe true
                    allowed("2026-10-31T02:30:00Z") shouldBe true
                }
            }
            `when`("종료 시각이 지났으면") {
                then("허용하지 않는다") { allowed("2026-10-31T02:30:01Z") shouldBe false }
            }
            `when`("예전 행처럼 날짜와 시각 사이가 공백이면") {
                then("같은 시각으로 읽는다") {
                    allowed("2026-10-31T01:20:00Z", "2026-10-31 10:30", "2026-10-31 11:30") shouldBe true
                    allowed("2026-10-31T01:19:00Z", "2026-10-31 10:30", "2026-10-31 11:30") shouldBe false
                }
            }
            `when`("일정이 없거나 읽을 수 없으면") {
                then("막지 않는다") {
                    allowed("2026-10-31T00:00:00Z", null, null) shouldBe true
                    allowed("2026-10-31T00:00:00Z", "", ended) shouldBe true
                    allowed("2026-10-31T00:00:00Z", "invalid", ended) shouldBe true
                    allowed("2026-10-31T00:00:00Z", started, "invalid") shouldBe true
                }
            }
        }
    }
}
