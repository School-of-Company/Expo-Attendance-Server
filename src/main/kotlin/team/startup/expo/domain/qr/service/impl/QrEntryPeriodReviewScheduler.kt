package team.startup.expo.domain.qr.service.impl

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/** 기간 확인을 기다리는 종이 QR 입장을 주기적으로 다시 확인한다. `qr-entry-review.enabled=false`로 끈다(테스트에서 끈다). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["qr-entry-review.enabled"], havingValue = "true", matchIfMissing = true)
class QrEntryPeriodReviewScheduler(
    private val qrEntryPeriodReviewService: QrEntryPeriodReviewService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${qr-entry-review.interval-ms:300000}", initialDelayString = "\${qr-entry-review.interval-ms:300000}")
    fun review() {
        try {
            qrEntryPeriodReviewService.reviewPending()
        } catch (e: Exception) {
            // 다음 주기에 다시 시도한다
            logger.warn("종이 QR 입장 기간 재확인 실패: 원인={}", e.javaClass.simpleName)
        }
    }
}
