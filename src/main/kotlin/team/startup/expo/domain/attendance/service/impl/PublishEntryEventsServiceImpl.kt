package team.startup.expo.domain.attendance.service.impl

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntryOutbox
import team.startup.expo.domain.attendance.event.EntryRecordedEvent
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.attendance.service.PublishEntryEventsService
import team.startup.expo.global.kafka.EntryEventProperties
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime
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
     * 선택한 행을 잠근 채 한 트랜잭션에서 보낸다. 하나가 실패하면 브로커 문제일 가능성이 커서 뒤의 이벤트는
     * 다음 주기로 미루고, 그때까지 성공한 이벤트는 `PUBLISHED`로 반영한다.
     */
    @Transactional
    override fun publishPending(): Int {
        var published = 0
        for (outbox in entryOutboxRepository.findPendingForUpdate(properties.relay.batchSize)) {
            if (!send(outbox)) break
            outbox.markPublished(LocalDateTime.now(clock))
            published++
        }
        return published
    }

    @Transactional
    override fun deleteExpired(): Int =
        entryOutboxRepository.deletePublishedBefore(LocalDateTime.now(clock).minusDays(properties.relay.retentionDays))

    private fun send(outbox: EntryOutbox): Boolean {
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
            true
        } catch (e: Exception) {
            // 전화번호가 들어 있는 페이로드는 로그에 남기지 않는다
            logger.warn("입장 이벤트 발행 실패: eventId={}, 원인={}", event.eventId, e.javaClass.simpleName)
            false
        }
    }
}
