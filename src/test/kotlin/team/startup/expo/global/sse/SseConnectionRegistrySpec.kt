package team.startup.expo.global.sse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Expo-Application-Server `SseConnectionRegistryTests`(#162)와 같은 경우를 확인한다. */
class SseConnectionRegistrySpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val emitters = CopyOnWriteArrayList<RecordingEmitter>()
    private var behavior: (Long) -> RecordingEmitter = { RecordingEmitter(it) }
    private val registries = CopyOnWriteArrayList<SseConnectionRegistry<String>>()

    private fun registry(limits: SseLimits = SseLimits(10, 10, Duration.ofMinutes(30), Duration.ofMinutes(1))) =
        SseConnectionRegistry<String>("test", limits, { it.toString() }) { timeout ->
            behavior(timeout).also(emitters::add)
        }.also(registries::add)

    init {
        afterEach { registries.forEach { it.close() } }

        given("같은 키의 구독자 둘과 다른 키의 구독자 하나가 있을 때") {
            `when`("한 키에 이벤트를 보내면") {
                val registry = registry()
                registry.open("a")
                registry.open("a")
                registry.open("b")
                registry.publish("a", SseMessage("1", "changed", mapOf("key" to "a")))

                then("그 키의 연결에만 닿는다") {
                    eventually { emitters.take(2).all { it.events() == listOf("id:1|event:changed|data:{key=a}") } }
                    Thread.sleep(100)
                    emitters[2].events() shouldBe emptyList()
                }
            }
        }

        given("연결 수 한도가 있을 때") {
            `when`("키당 한도와 전체 한도를 넘게 열면") {
                val registry = registry(SseLimits(3, 2, Duration.ofMinutes(1), Duration.ofMinutes(1)))
                registry.open("a")
                registry.open("a")

                then("한도 예외를 던지고 연결 수를 세지 않는다") {
                    shouldThrow<SseConnectionLimitException> { registry.open("a") }
                    registry.open("b")
                    shouldThrow<SseConnectionLimitException> { registry.open("c") }
                    registry.connectionCount() shouldBe 3
                    registry.connectionCount("a") shouldBe 2
                }
            }
        }

        given("느린 수신자가 있을 때") {
            `when`("보내는 동안 이벤트가 100개 쌓이면") {
                val release = CountDownLatch(1)
                behavior = { RecordingEmitter(it, block = release) }
                val registry = registry()
                registry.open("a")
                eventually { emitters.single().blocked }
                repeat(100) { registry.publish("a", SseMessage("$it", "changed", it)) }
                release.countDown()

                then("마지막 이벤트 하나만 보낸다") {
                    eventually { emitters.single().events().isNotEmpty() }
                    Thread.sleep(200)
                    emitters.single().events() shouldBe listOf("id:99|event:changed|data:99")
                }
            }
        }

        given("전송이 실패하는 연결이") {
            `when`("열리면") {
                behavior = { RecordingEmitter(it, fail = true) }
                val registry = registry()
                registry.open("a")

                then("닫히고 잊힌다") {
                    eventually { registry.connectionCount() == 0 }
                    registry.publish("a", SseMessage("1", "changed", 1))
                    registry.connectionCount("a") shouldBe 0
                }
            }
        }

        given("heartbeat 간격이 짧을 때") {
            `when`("연결을 열어 두면") {
                val registry = registry(SseLimits(10, 10, Duration.ofMinutes(1), Duration.ofMillis(50)))
                registry.open("a")

                then("heartbeat는 주석이며 데이터 이벤트가 아니다") {
                    eventually { emitters.single().comments() >= 3 }
                    emitters.single().events() shouldBe emptyList()
                }
            }
        }

        given("연결 수명이 30분일 때") {
            `when`("연결을 열면") {
                registry().open("a")

                then("emitter timeout이 연결 수명과 같다") {
                    emitters.single().timeout shouldBe Duration.ofMinutes(30).toMillis()
                }
            }
        }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met in time" }
            Thread.sleep(10)
        }
    }

    class RecordingEmitter(
        timeout: Long,
        private val block: CountDownLatch? = null,
        private val fail: Boolean = false,
    ) : SseEmitter(timeout) {
        private val sent = CopyOnWriteArrayList<String>()

        @Volatile var blocked = false

        override fun send(builder: SseEventBuilder) {
            if (fail) throw IOException("client gone")
            if (block != null && sent.isEmpty()) {
                blocked = true
                block.await(5, TimeUnit.SECONDS)
            }
            sent.add(
                builder
                    .build()
                    .joinToString("") { it.data.toString() }
                    .trim()
                    .lines()
                    .joinToString("|"),
            )
        }

        fun events() = sent.filterNot { it.startsWith(":") }

        fun comments() = sent.count { it.startsWith(":") }
    }
}
