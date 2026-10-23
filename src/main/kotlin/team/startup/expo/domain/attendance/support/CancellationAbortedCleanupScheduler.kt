package team.startup.expo.domain.attendance.support

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/** 보관 기간이 지난 중단된 취소 기록을 주기적으로 지운다. `cancellation-aborted-retention.days`가 0이면(기본) 등록하지 않는다. */
@Configuration
@EnableScheduling
@ConditionalOnExpression("\${cancellation-aborted-retention.days:0} > 0")
class CancellationAbortedCleanupScheduler(
    private val cancellationAbortedCleanupService: CancellationAbortedCleanupService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    fun cleanup() {
        try {
            cancellationAbortedCleanupService.cleanup()
        } catch (e: Exception) {
            // 다음 주기에 다시 시도한다
            logger.warn("중단된 취소 기록 정리 실패: 원인={}", e.javaClass.simpleName)
        }
    }

    private companion object {
        const val INTERVAL_MS = 3_600_000L
        const val INITIAL_DELAY_MS = 600_000L
    }
}
