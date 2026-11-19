package team.startup.expo.domain.attendance.support

import com.github.benmanes.caffeine.cache.Ticker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.global.config.AdminStatsCacheProperties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AdminStatsCacheSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val nanos = AtomicLong()
    private val ticker = Ticker { nanos.get() }
    private val cache = AdminStatsCache(AdminStatsCacheProperties(ttlMillis = 1000), ticker)
    private val loads = AtomicInteger()

    private fun advanceMillis(millis: Long) {
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis))
    }

    private fun read(
        expoId: String = "expo-1",
        request: Any = "today",
        kind: String = "summary",
    ): Int = cache.get(kind, expoId, request) { loads.incrementAndGet() }

    init {
        given("현황 조회 결과 공유(#255)에서") {
            `when`("같은 조회를 TTL 안에 다시 하면") {
                then("한 번만 계산하고 같은 결과를 돌려준다") {
                    read() shouldBe 1
                    advanceMillis(999)
                    read() shouldBe 1
                    loads.get() shouldBe 1
                }
            }

            `when`("TTL이 지나면") {
                then("다시 계산한다") {
                    read()
                    advanceMillis(1000)
                    read() shouldBe 2
                }
            }

            `when`("조회 종류, 박람회, 요청 조건이 다르면") {
                then("각각 계산한다") {
                    read()
                    read(kind = "hourly")
                    read(expoId = "expo-2")
                    read(request = "all")
                    loads.get() shouldBe 4
                }
            }

            `when`("계산이 실패하면") {
                then("실패를 기억하지 않고 다음 조회가 다시 계산한다") {
                    shouldThrow<IllegalStateException> { cache.get<Int>("summary", "expo-1", "today") { error("db down") } }
                    read() shouldBe 1
                }
            }

            `when`("그 박람회의 변경 신호로 버리면") {
                then("그 박람회만 다시 계산하고 다른 박람회 결과는 그대로 쓴다") {
                    read()
                    read(expoId = "expo-2")
                    cache.invalidate("expo-1")
                    read() shouldBe 3
                    read(expoId = "expo-2") shouldBe 2
                }
            }

            `when`("모두 버리면") {
                then("모두 다시 계산한다") {
                    read()
                    read(expoId = "expo-2")
                    cache.invalidateAll()
                    read()
                    read(expoId = "expo-2")
                    loads.get() shouldBe 4
                }
            }

            `when`("TTL이 0이면") {
                val off = AdminStatsCache(AdminStatsCacheProperties(ttlMillis = 0), ticker)

                then("매번 계산한다") {
                    repeat(3) { off.get("summary", "expo-1", "today") { loads.incrementAndGet() } }
                    loads.get() shouldBe 3
                }
            }

            `when`("같은 조회가 동시에 들어오면") {
                then("한 번만 계산하고 나머지는 그 결과를 기다린다") {
                    val started = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val executor = Executors.newFixedThreadPool(5)
                    try {
                        val first =
                            executor.submit<Int> {
                                cache.get("summary", "expo-1", "today") {
                                    started.countDown()
                                    release.await(5, TimeUnit.SECONDS)
                                    loads.incrementAndGet()
                                }
                            }
                        started.await(5, TimeUnit.SECONDS) shouldBe true
                        val waiters = List(4) { executor.submit<Int> { read() } }
                        release.countDown()
                        (waiters + first).map { it.get(5, TimeUnit.SECONDS) }.toSet() shouldBe setOf(1)
                        loads.get() shouldBe 1
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }

            `when`("계산 중에 변경 신호로 버리면") {
                then("그 뒤의 조회는 계산 중인 결과를 기다리지 않고 새로 계산한다") {
                    val started = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val executor = Executors.newSingleThreadExecutor()
                    try {
                        val before =
                            executor.submit<String> {
                                cache.get("summary", "expo-1", "today") {
                                    started.countDown()
                                    release.await(5, TimeUnit.SECONDS)
                                    "변경 전"
                                }
                            }
                        started.await(5, TimeUnit.SECONDS) shouldBe true
                        cache.invalidate("expo-1")
                        cache.get("summary", "expo-1", "today") { "변경 후" } shouldBe "변경 후"
                        release.countDown()
                        before.get(5, TimeUnit.SECONDS) shouldBe "변경 전"
                        cache.get("summary", "expo-1", "today") { "다시 계산" } shouldBe "변경 후"
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }
        }
    }
}
