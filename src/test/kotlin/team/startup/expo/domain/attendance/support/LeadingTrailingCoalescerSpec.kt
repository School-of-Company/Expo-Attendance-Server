package team.startup.expo.domain.attendance.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.support.MutableClock
import java.time.Duration

class LeadingTrailingCoalescerSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val clock = MutableClock()
    private val sent = mutableListOf<String>()
    private val tasks = mutableListOf<Pair<Long, Runnable>>()
    private val coalescer =
        LeadingTrailingCoalescer(clock, 1_000, { delay, task -> tasks += delay to task }, { sent += it })

    /** 예약된 뒤쪽 발송을 지연만큼 시간을 흘린 뒤 실행한다. */
    private fun runScheduled() {
        val (delay, task) = tasks.removeAt(0)
        clock.advance(Duration.ofMillis(delay))
        task.run()
    }

    init {
        given("조용하던 박람회에") {
            `when`("변경이 한 번 오면") {
                coalescer.signal("e1")

                then("바로 보내고 예약하지 않는다") {
                    sent shouldBe listOf("e1")
                    tasks.size shouldBe 0
                }
            }

            `when`("간격 안에 변경이 연달아 오면") {
                coalescer.signal("e1")
                clock.advance(Duration.ofMillis(100))
                repeat(5) { coalescer.signal("e1") }

                then("첫 변경은 바로 가고 나머지는 하나로 합쳐 간격 끝에 한 번 간다") {
                    sent shouldBe listOf("e1")
                    tasks.size shouldBe 1
                    tasks[0].first shouldBe 900L
                    runScheduled()
                    sent shouldBe listOf("e1", "e1")
                    tasks.size shouldBe 0
                }
            }

            `when`("연속 변경이 끝나고 간격이 지난 뒤 다음 변경이 오면") {
                coalescer.signal("e1")
                clock.advance(Duration.ofMillis(100))
                coalescer.signal("e1")
                runScheduled()
                clock.advance(Duration.ofMillis(1_000))
                coalescer.signal("e1")

                then("다시 바로 보낸다") {
                    sent.size shouldBe 3
                    tasks.size shouldBe 0
                }
            }

            `when`("뒤쪽 발송 직후 간격 안에 변경이 또 오면") {
                coalescer.signal("e1")
                clock.advance(Duration.ofMillis(100))
                coalescer.signal("e1")
                runScheduled()
                clock.advance(Duration.ofMillis(200))
                coalescer.signal("e1")

                then("뒤쪽 발송 시각부터 간격을 다시 재서 한 번 더 예약한다") {
                    sent.size shouldBe 2
                    tasks.size shouldBe 1
                    tasks[0].first shouldBe 800L
                }
            }

            `when`("다른 박람회 변경이 오면") {
                coalescer.signal("e1")
                coalescer.signal("e2")

                then("박람회마다 따로 바로 보낸다") {
                    sent shouldBe listOf("e1", "e2")
                }
            }
        }

        given("간격이 0이면") {
            val off = LeadingTrailingCoalescer(clock, 0, { _, _ -> error("예약하지 않는다") }, { sent += it })

            then("모든 변경을 바로 보낸다") {
                repeat(3) { off.signal("e1") }
                sent.size shouldBe 3
            }
        }
    }
}
