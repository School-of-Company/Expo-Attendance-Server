package team.startup.expo.domain.attendance.support

import com.github.benmanes.caffeine.cache.Ticker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.http.HttpStatus
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterSessionCacheProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PreregisterSessionCacheSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val nanos = AtomicLong()
    private val ticker = Ticker { nanos.get() }

    private fun advanceSeconds(seconds: Long) {
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds))
    }

    private fun newCache(ttlSeconds: Long = 30) =
        PreregisterSessionCache(
            PreregisterSessionCacheProperties(ttlSeconds = ttlSeconds),
            ticker,
        )

    private val cache = newCache()
    private val calls = AtomicInteger()
    private val start = Instant.parse("2026-05-10T03:00:00Z")
    private val session = PreregisterSessionResDto(1, start, start.plusSeconds(3600))

    private fun load(): PreregisterSessionResDto {
        calls.incrementAndGet()
        return session
    }

    /** 스레드 풀의 스레드를 모아, 몇 개가 대기 상태에 들어갔는지 볼 수 있게 한다. */
    private class TrackedPool(
        size: Int,
    ) {
        val threads = mutableListOf<Thread>()
        val executor =
            Executors.newFixedThreadPool(size) { runnable ->
                Thread(runnable).also { synchronized(threads) { threads += it } }
            }

        fun awaitWaiting(count: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (synchronized(threads) { threads.count { it.state == Thread.State.WAITING } } < count) {
                check(System.nanoTime() < deadline) { "threads did not start waiting" }
                Thread.onSpinWait()
            }
        }
    }

    init {
        given("회차 시간을 한 번 읽은 뒤") {
            cache.get("expo-1", 1) { load() }
            `when`("TTL 안에 같은 키를 다시 읽으면") {
                advanceSeconds(29)
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
                advanceSeconds(31)
                then("다시 부른다") {
                    cache.get("expo-1", 1) { load() }
                    calls.get() shouldBe 2
                }
            }
        }

        given("원격 호출이 실패하면") {
            var attempts = 0
            `when`("실패는") {
                shouldThrow<ExpectedException> {
                    cache.get("expo-1", 1) {
                        attempts++
                        throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.")
                    }
                }
                then("기억하지 않고 다음 조회가 다시 부른다") {
                    cache.get("expo-1", 1) {
                        attempts++
                        session
                    } shouldBe session
                    attempts shouldBe 2
                }
            }
        }

        given("TTL 0") {
            val off = newCache(ttlSeconds = 0)
            then("항상 부른다") {
                off.get("expo-1", 1) { load() }
                off.get("expo-1", 1) { load() }
                calls.get() shouldBe 2
            }
        }

        given("같은 키를 동시에 읽는 스레드 8개") {
            val pool = TrackedPool(8)
            val remote = AtomicInteger()
            val release = CountDownLatch(1)

            `when`("원격 응답을 기다리는 동안 나머지 7개가 모두 기다림에 들어간 뒤 응답하면") {
                val futures =
                    (1..8).map {
                        pool.executor.submit<PreregisterSessionResDto> {
                            cache.get("expo-1", 1) {
                                remote.incrementAndGet()
                                release.await(5, TimeUnit.SECONDS)
                                session
                            }
                        }
                    }
                // 원격 호출 1개는 TIMED_WAITING, 나머지 7개는 그 결과를 join하며 WAITING이다
                pool.awaitWaiting(7)
                release.countDown()
                then("원격 호출은 한 번이고 모두 같은 값을 받는다") {
                    futures.map { it.get(5, TimeUnit.SECONDS) }.toSet() shouldBe setOf(session)
                    remote.get() shouldBe 1
                    pool.executor.shutdown()
                }
            }

            `when`("원격 호출이 실패하면") {
                val failure = ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.")
                val futures =
                    (1..3).map {
                        pool.executor.submit<PreregisterSessionResDto> {
                            cache.get("expo-1", 1) {
                                remote.incrementAndGet()
                                release.await(5, TimeUnit.SECONDS)
                                throw failure
                            }
                        }
                    }
                pool.awaitWaiting(2)
                release.countDown()
                then("기다린 쪽도 같은 예외를 받는다") {
                    futures.forEach {
                        shouldThrow<ExecutionException> { it.get(5, TimeUnit.SECONDS) }.cause shouldBeSameInstanceAs failure
                    }
                    remote.get() shouldBe 1
                    pool.executor.shutdown()
                }
            }
        }

        given("한 키의 원격 호출이 끝나지 않았을 때") {
            val loading = CountDownLatch(1)
            val release = CountDownLatch(1)
            val slow = Executors.newSingleThreadExecutor()
            val pending =
                slow.submit<PreregisterSessionResDto> {
                    cache.get("expo-1", 1) {
                        loading.countDown()
                        // 다른 키 조회가 이 호출에 막혔다면 release가 오지 않아 실패한다
                        check(release.await(5, TimeUnit.SECONDS)) { "other key was blocked" }
                        session
                    }
                }
            loading.await(5, TimeUnit.SECONDS)
            `when`("다른 키를 읽으면") {
                then("기다리지 않고 바로 부른다") {
                    cache.get("expo-1", 2) { load() } shouldBe session
                    calls.get() shouldBe 1
                    release.countDown()
                    pending.get(5, TimeUnit.SECONDS) shouldBe session
                    slow.shutdown()
                }
            }
        }
    }
}
