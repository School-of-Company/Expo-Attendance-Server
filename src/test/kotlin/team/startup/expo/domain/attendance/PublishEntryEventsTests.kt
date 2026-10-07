package team.startup.expo.domain.attendance

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.bean.override.mockito.MockitoBean
import team.startup.expo.domain.attendance.entity.EntryOutbox
import team.startup.expo.domain.attendance.entity.OutboxStatus
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.attendance.service.PublishEntryEventsService
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class PublishEntryEventsTests : IntegrationTestSupport() {
    @Autowired
    lateinit var publishEntryEventsService: PublishEntryEventsService

    @Autowired
    lateinit var entryOutboxRepository: EntryOutboxRepository

    @MockitoBean
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val today = LocalDate.of(2026, 10, 7)

    @BeforeEach
    fun setUp() {
        entryOutboxRepository.deleteAll()
    }

    @Test
    fun `쌓인 이벤트를 문자 서비스가 받는 형식으로 토픽에 발행하고 PUBLISHED로 바꾼다`() {
        val first = saveOutbox(participantId = 1, createdAt = LocalDateTime.of(2026, 10, 7, 9, 0))
        val second = saveOutbox(participantId = 2, createdAt = LocalDateTime.of(2026, 10, 7, 9, 5))
        sendSucceeds()

        publishEntryEventsService.publishPending() shouldBe 2

        entryOutboxRepository.findAll().map { it.status }.toSet() shouldBe setOf(OutboxStatus.PUBLISHED)
        entryOutboxRepository.findAll().all { it.publishedAt != null } shouldBe true
        verify(kafkaTemplate).send(TOPIC, first.eventId.toString(), expectedPayload(first, id = 1))
        verify(kafkaTemplate).send(TOPIC, second.eventId.toString(), expectedPayload(second, id = 2))
    }

    @Test
    fun `이미 발행한 이벤트는 다시 보내지 않는다`() {
        saveOutbox(participantId = 1)
        sendSucceeds()

        publishEntryEventsService.publishPending() shouldBe 1
        publishEntryEventsService.publishPending() shouldBe 0
    }

    @Test
    fun `브로커가 실패하면 PENDING으로 남기고 뒤의 이벤트는 미룬다`() {
        val first = saveOutbox(participantId = 1, createdAt = LocalDateTime.of(2026, 10, 7, 9, 0))
        val second = saveOutbox(participantId = 2, createdAt = LocalDateTime.of(2026, 10, 7, 9, 5))
        doReturn(CompletableFuture.completedFuture(mock(SendResult::class.java)))
            .doReturn(CompletableFuture.failedFuture<SendResult<String, String>>(RuntimeException("broker down")))
            .`when`(kafkaTemplate)
            .send(anyString(), anyString(), anyString())

        publishEntryEventsService.publishPending() shouldBe 1

        val statuses = entryOutboxRepository.findAll().associate { it.id to it.status }
        statuses[first.id] shouldBe OutboxStatus.PUBLISHED
        statuses[second.id] shouldBe OutboxStatus.PENDING

        // 브로커가 돌아오면 남은 이벤트가 나간다
        sendSucceeds()
        publishEntryEventsService.publishPending() shouldBe 1
        entryOutboxRepository.findAll().map { it.status }.toSet() shouldBe setOf(OutboxStatus.PUBLISHED)
    }

    @Test
    fun `발행할 이벤트가 없으면 브로커를 부르지 않는다`() {
        publishEntryEventsService.publishPending() shouldBe 0

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `보관 기간이 지난 발행 완료 이벤트만 지운다`() {
        val oldPublished = saveOutbox(participantId = 1).apply { markPublished(LocalDateTime.now().minusDays(8)) }
        val recentPublished = saveOutbox(participantId = 2).apply { markPublished(LocalDateTime.now().minusDays(1)) }
        entryOutboxRepository.saveAll(listOf(oldPublished, recentPublished))
        val pending = saveOutbox(participantId = 3, createdAt = LocalDateTime.now().minusDays(30))

        publishEntryEventsService.deleteExpired() shouldBe 1

        entryOutboxRepository.findAll().map { it.participantId }.sorted() shouldContainExactly listOf(2L, 3L)
        entryOutboxRepository.findById(pending.id!!).get().status shouldBe OutboxStatus.PENDING
    }

    private fun sendSucceeds() {
        doReturn(CompletableFuture.completedFuture(mock(SendResult::class.java)))
            .`when`(kafkaTemplate)
            .send(anyString(), anyString(), anyString())
    }

    private fun expectedPayload(
        outbox: EntryOutbox,
        id: Long,
    ) =
        """{"eventId":"${outbox.eventId}","expoId":"expo-relay","participationType":"STANDARD","id":$id,"phoneNumber":"${outbox.phoneNumber}"}"""

    private fun saveOutbox(
        participantId: Long,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): EntryOutbox =
        entryOutboxRepository.save(
            EntryOutbox(
                expoId = "expo-relay",
                participantId = participantId,
                // 같은 박람회·날짜에서 번호가 겹치면 하루 한 통만 남으므로 참가자마다 다른 번호를 쓴다
                phoneNumber = "010%08d".format(participantId),
                attendanceDate = today,
                createdAt = createdAt,
            ),
        )

    private companion object {
        const val TOPIC = "attention.entry.recorded"
    }
}
