package team.startup.expo.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트가 시각을 앞으로 돌릴 수 있는 시계. 캐시 만료처럼 시간이 흐르는 동작을 확인할 때 쓴다. */
class MutableClock(
    private var current: Instant = UnitTestSupport.NOW,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current
}
