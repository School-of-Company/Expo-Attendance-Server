package team.startup.expo.domain.attendance.support

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterSessionCacheProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PreregisterSessionCacheSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private class MutableClock(
        var now: Instant = Instant.parse("2026-05-10T03:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock()
    private val cache = PreregisterSessionCache(PreregisterSessionCacheProperties(ttlSeconds = 30), clock)
    private val calls = AtomicInteger()
    private val session = PreregisterSessionResDto(1, clock.now, clock.now.plusSeconds(3600))

    private fun load(): PreregisterSessionResDto {
        calls.incrementAndGet()
        return session
    }

    init {
        given("회차 시간을 한 번 읽은 뒤") {
            cache.get("expo-1", 1) { load() }
            `when`("TTL 안에 같은 키를 다시 읽으면") {
                clock.now = clock.now.plusSeconds(29)
                then("원격 호출 없이 기억한 값을 쓴다") {
                    cache.get("expo-1", 1) { load() } shouldBe session
                    calls.get() shouldBe 1
                }
                then("다른 회차나 박람회는 따로 부른다") {
                    cache.get("expo-1", 2) { load() }
                    cache.get("expo-2", 1) { load() }
                    calls.get() shouldBe 3
                }
            }
            `when`("TTL이 지나면") {
                clock.now = clock.now.plusSeconds(31)
                then("다시 부른다") {
                    cache.get("expo-1", 1) { load() }
                    calls.get() shouldBe 2
                }
            }
        }

        given("원격 호출이 실패하면") {
            val failing = PreregisterSessionCache(PreregisterSessionCacheProperties(ttlSeconds = 30), clock)
            var attempts = 0
            `when`("실패는") {
                shouldThrow<ExpectedException> {
                    failing.get("expo-1", 1) {
                        attempts++
                        throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.")
                    }
                }
                then("기억하지 않고 다음 조회가 다시 부른다") {
                    failing.get("expo-1", 1) {
                        attempts++
                        session
                    } shouldBe session
                    attempts shouldBe 2
                }
            }
        }

        given("TTL 0") {
            val off = PreregisterSessionCache(PreregisterSessionCacheProperties(ttlSeconds = 0), clock)
            then("항상 부른다") {
                off.get("expo-1", 1) { load() }
                off.get("expo-1", 1) { load() }
                calls.get() shouldBe 2
            }
        }

        given("같은 키를 동시에 읽는 스레드 여러 개") {
            val concurrent = PreregisterSessionCache(PreregisterSessionCacheProperties(ttlSeconds = 30), clock)
            val remote = AtomicInteger()
            val release = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(8)
            `when`("원격 응답을 기다리는 동안 모두 들어오면") {
                val futures =
                    (1..8).map {
                        pool.submit<PreregisterSessionResDto> {
                            concurrent.get("expo-1", 1) {
                                remote.incrementAndGet()
                                release.await(5, TimeUnit.SECONDS)
                                session
                            }
                        }
                    }
                Thread.sleep(200)
                release.countDown()
                then("원격 호출은 한 번이고 모두 같은 값을 받는다") {
                    futures.map { it.get(5, TimeUnit.SECONDS) }.toSet() shouldBe setOf(session)
                    remote.get() shouldBe 1
                    pool.shutdown()
                }
            }
        }
    }
}
