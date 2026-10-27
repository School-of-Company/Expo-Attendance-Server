package team.startup.expo.global.client.expo.projection

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * `expo.expo.changed`를 받아 박람회 프로젝션을 갱신한다(#240). 형식이 잘못된 메시지는 [IllegalArgumentException]으로 바로 DLT에
 * 보낸다(`ExpoProjectionKafkaConfig`). 메시지 본문은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["expo-projection.enabled"], havingValue = "true")
class ExpoChangedConsumer(
    private val mapper: ObjectMapper,
    private val updater: ExpoProjectionUpdater,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [ExpoChangedEvent.TOPIC],
        groupId = "\${expo-projection.group-id}",
        containerFactory = "expoChangedContainerFactory",
    )
    fun consume(record: ConsumerRecord<String?, String?>) {
        val event = parse(record.key(), record.value())
        if (!updater.apply(event)) {
            log.info("ExpoChanged skipped as not newer: partition={}, offset={}", record.partition(), record.offset())
        }
    }

    /** 메시지를 읽는다. 키와 `expoId`가 다르거나, 모르는 `schemaVersion`이거나, 필요한 값이 없으면 [IllegalArgumentException]이다. */
    fun parse(
        key: String?,
        value: String?,
    ): ExpoChangedEvent =
        try {
            val body = mapper.readTree(requireNotNull(value))
            require(body.isObject)
            require(body.path("schemaVersion").let { it.isIntegralNumber && it.asInt() == ExpoChangedEvent.SCHEMA_VERSION })
            val expoId = text(body, "expoId")
            require(key == expoId)
            val version = body.path("version")
            require(version.isIntegralNumber && version.canConvertToLong() && version.asLong() > 0)
            val deleted = body.path("deleted")
            require(deleted.isBoolean)
            val event =
                ExpoChangedEvent(
                    eventId = text(body, "eventId"),
                    version = version.asLong(),
                    expoId = expoId,
                    deleted = deleted.asBoolean(),
                )
            if (event.deleted) event else withState(event, body)
        } catch (_: Exception) {
            // 파서 예외는 원문 일부를 담을 수 있어 남기지 않는다
            throw IllegalArgumentException("Invalid ExpoChanged event")
        }

    private fun withState(
        event: ExpoChangedEvent,
        body: JsonNode,
    ): ExpoChangedEvent {
        val expo = body.path("expo")
        require(expo.isObject)
        return event.copy(
            expo =
                ExpoChangedEvent.ExpoState(
                    startedDay = text(expo, "startedDay"),
                    finishedDay = text(expo, "finishedDay"),
                    deleting = expo.path("deleting").let { it.isBoolean && it.asBoolean() },
                ),
            standardPrograms =
                array(body, "standardPrograms").map {
                    ExpoChangedEvent.StandardProgramState(id(it), optionalText(it, "startedAt"), optionalText(it, "endedAt"))
                },
            trainingPrograms = array(body, "trainingPrograms").map { ExpoChangedEvent.TrainingProgramState(id(it)) },
            preregisterSessions =
                array(body, "preregisterSessions").map {
                    ExpoChangedEvent.PreregisterSessionState(
                        id(it),
                        Instant.parse(text(it, "startedAt")),
                        Instant.parse(text(it, "endedAt")),
                    )
                },
        )
    }

    private fun text(
        node: JsonNode,
        field: String,
    ): String {
        val value = node.path(field)
        require(value.isString && value.asString().isNotBlank())
        return value.asString()
    }

    private fun optionalText(
        node: JsonNode,
        field: String,
    ): String? = node.path(field).takeIf { it.isString }?.asString()

    private fun id(node: JsonNode): Long {
        val id = node.path("id")
        require(id.isIntegralNumber && id.canConvertToLong())
        return id.asLong()
    }

    private fun array(
        node: JsonNode,
        field: String,
    ): List<JsonNode> {
        val value = node.path(field)
        require(value.isArray)
        return value.toList()
    }
}
