package team.startup.expo.domain.attendance.service.impl

import org.apache.kafka.common.errors.ApiException
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntryOutbox
import team.startup.expo.domain.attendance.event.EntryRecordedEvent
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.attendance.service.PublishEntryEventsService
import team.startup.expo.global.kafka.EntryEventProperties
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * 아웃박스에 쌓인 입장 이벤트를 발행한다. 입장 기록과 이벤트 발행이 한 트랜잭션으로 묶이지 않으므로, 이벤트를
 * 먼저 DB에 남기고 여기서 브로커로 보낸다. 브로커가 죽어 있으면 `PENDING`으로 남아 다음 주기에 다시 보낸다.
 *
 * 발행은 최소 한 번이다. 브로커가 받은 직후 DB 반영 전에 죽으면 같은 이벤트를 다시 보내므로, 소비자는
 * `eventId`로 중복을 걸러낸다.
 */
@Service
class PublishEntryEventsServiceImpl(
    private val entryOutboxRepository: EntryOutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val properties: EntryEventProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : PublishEntryEventsService {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 선택한 행을 잠근 채 한 트랜잭션에서 보낸다. 브로커 문제로 실패하면 뒤의 이벤트는 다음 주기로 미루고, 그때까지
     * 성공한 이벤트는 `PUBLISHED`로 반영한다. 다시 보내도 소용없는 이벤트(너무 크거나 직렬화할 수 없는 경우)는
     * `FAILED`로 포기하고 다음 이벤트를 계속 보내, 하나가 뒤의 모든 이벤트를 막지 않게 한다.
     */
    @Transactional
    override fun publishPending(): Int {
        var published = 0
        for (outbox in entryOutboxRepository.findPendingForUpdate(properties.relay.batchSize)) {
            when (send(outbox)) {
                SendResult.SENT -> {
                    outbox.markPublished(LocalDateTime.now(clock))
                    published++
                }

                SendResult.GAVE_UP -> {
                    outbox.markFailed(LocalDateTime.now(clock))
                }

                SendResult.RETRY_LATER -> {
                    break
                }
            }
        }
        return published
    }

    @Transactional
    override fun deleteExpired(): Int =
        entryOutboxRepository.deletePublishedBefore(LocalDateTime.now(clock).minusDays(properties.relay.retentionDays))

    private fun send(outbox: EntryOutbox): SendResult {
        val event =
            EntryRecordedEvent(
                eventId = outbox.eventId.toString(),
                expoId = outbox.expoId,
                id = outbox.participantId,
                phoneNumber = outbox.phoneNumber,
            )
        return try {
            kafkaTemplate
                .send(properties.recordedTopic, event.eventId, objectMapper.writeValueAsString(event))
                .get(properties.relay.sendTimeoutMs, TimeUnit.MILLISECONDS)
            SendResult.SENT
        } catch (e: Exception) {
            // 전화번호가 들어 있는 페이로드는 로그에 남기지 않는다
            val result = if (isPermanent(e)) SendResult.GAVE_UP else SendResult.RETRY_LATER
            logger.warn("입장 이벤트 발행 실패({}): eventId={}, 원인={}", result, event.eventId, rootName(e))
            result
        }
    }

    /** 브로커가 다시 시도해도 받지 않는 오류(재시도 불가능한 Kafka 오류)와 이벤트를 직렬화하지 못하는 오류다. */
    private fun isPermanent(e: Exception): Boolean {
        if (e is JacksonException) return true
        val cause = (e as? ExecutionException)?.cause ?: return false
        return cause is ApiException && cause !is RetriableException
    }

    private fun rootName(e: Exception): String = ((e as? ExecutionException)?.cause ?: e).javaClass.simpleName

    private enum class SendResult { SENT, RETRY_LATER, GAVE_UP }
}
