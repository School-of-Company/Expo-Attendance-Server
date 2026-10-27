package team.startup.expo.global.client.expo.projection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class ExpoChangedConsumerSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val updater = mockk<ExpoProjectionUpdater>()
    private val consumer = ExpoChangedConsumer(JsonMapper.builder().build(), updater)
    private val expoId = "0192f0c8-0000-7000-8000-000000000001"

    private fun state(
        version: Long = 1042,
        schemaVersion: Int = 1,
    ) = """
        {"schemaVersion":$schemaVersion,"eventId":"6f1c3a0e-8a43-4d4e-9a55-0d6f3e1b2c11","version":$version,
         "occurredAt":"2030-01-01T00:00:00.123456Z","expoId":"$expoId","deleted":false,"unknownTopLevel":1,
         "expo":{"title":"예시 박람회","startedDay":"2030-01-01","finishedDay":"2030-01-02","deleting":false,"location":"광주"},
         "standardPrograms":[{"id":1,"title":"일반","startedAt":"2030-01-01T09:00","endedAt":"2030-01-01T10:00","policy":null}],
         "trainingPrograms":[{"id":5,"title":"연수","category":"ESSENTIAL"}],
         "preregisterSessions":[{"id":7,"startedAt":"2030-01-01T01:00:00Z","endedAt":"2030-01-01T02:00:00Z","capacity":30}]}
        """.trimIndent()

    private val tombstone =
        """{"schemaVersion":1,"eventId":"e-2","version":1043,"occurredAt":"2030-01-01T00:00:01Z","expoId":"$expoId","deleted":true}"""

    private fun record(
        value: String?,
        key: String? = expoId,
    ) = ConsumerRecord(ExpoChangedEvent.TOPIC, 0, 3L, key, value)

    init {
        given("expo.expo.changed 메시지를 받을 때") {
            `when`("전체 상태 메시지면") {
                every { updater.apply(any()) } returns true
                consumer.consume(record(state()))

                then("이 서비스가 쓰는 값만 읽어 프로젝션에 반영한다(모르는 필드는 무시)") {
                    verify(exactly = 1) {
                        updater.apply(
                            ExpoChangedEvent(
                                eventId = "6f1c3a0e-8a43-4d4e-9a55-0d6f3e1b2c11",
                                version = 1042,
                                expoId = expoId,
                                deleted = false,
                                expo = ExpoChangedEvent.ExpoState("2030-01-01", "2030-01-02", deleting = false),
                                standardPrograms = listOf(ExpoChangedEvent.StandardProgramState(1, "2030-01-01T09:00", "2030-01-01T10:00")),
                                trainingPrograms = listOf(ExpoChangedEvent.TrainingProgramState(5)),
                                preregisterSessions =
                                    listOf(
                                        ExpoChangedEvent.PreregisterSessionState(
                                            7,
                                            Instant.parse("2030-01-01T01:00:00Z"),
                                            Instant.parse("2030-01-01T02:00:00Z"),
                                        ),
                                    ),
                            ),
                        )
                    }
                }
            }

            `when`("tombstone이면") {
                every { updater.apply(any()) } returns true
                consumer.consume(record(tombstone))

                then("상태 없이 삭제로 반영한다") {
                    verify(exactly = 1) { updater.apply(ExpoChangedEvent("e-2", 1043, expoId, deleted = true)) }
                }
            }

            `when`("이미 반영한 version 이하라 버려지면") {
                every { updater.apply(any()) } returns false

                then("오류 없이 넘어간다(커밋되어 다시 오지 않는다)") {
                    consumer.consume(record(state()))
                    verify(exactly = 1) { updater.apply(any()) }
                }
            }
        }

        given("형식이 잘못된 메시지는") {
            mapOf(
                "키가 expoId와 다르면" to record(state(), key = "other"),
                "모르는 schemaVersion이면" to record(state(schemaVersion = 2)),
                "version이 0이면" to record(state(version = 0)),
                "JSON이 아니면" to record("not-json secret-body"),
                "값이 null이면" to record(null),
                "상태 메시지에 expo가 없으면" to record("""{"schemaVersion":1,"eventId":"e","version":1,"expoId":"$expoId","deleted":false}"""),
            ).forEach { (case, record) ->
                `when`(case) {
                    then("IllegalArgumentException으로 DLT에 보내고 본문은 예외에 담지 않는다") {
                        val e = shouldThrow<IllegalArgumentException> { consumer.consume(record) }
                        e.message shouldBe "Invalid ExpoChanged event"
                        e.cause shouldBe null
                        e.message.orEmpty() shouldNotContain "secret-body"
                        verify(exactly = 0) { updater.apply(any()) }
                    }
                }
            }
        }
    }
}
