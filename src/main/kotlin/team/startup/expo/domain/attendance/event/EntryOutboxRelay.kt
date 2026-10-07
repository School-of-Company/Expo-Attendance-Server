package team.startup.expo.domain.attendance.event

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.service.PublishEntryEventsService

/** 주기마다 아웃박스를 비운다. `entry-events.relay.enabled=false`면 만들어지지 않는다(테스트, 임시 중단). */
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["entry-events.relay.enabled"], havingValue = "true", matchIfMissing = true)
class EntryOutboxRelay(
    private val publishEntryEventsService: PublishEntryEventsService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${entry-events.relay.interval-ms:3000}")
    fun publish() {
        try {
            publishEntryEventsService.publishPending()
        } catch (e: Exception) {
            logger.error("입장 이벤트 릴레이 실패", e)
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    fun cleanUp() {
        try {
            val deleted = publishEntryEventsService.deleteExpired()
            if (deleted > 0) logger.info("발행을 마친 입장 이벤트 {}건을 지웠습니다.", deleted)
        } catch (e: Exception) {
            logger.error("입장 이벤트 정리 실패", e)
        }
    }
}
