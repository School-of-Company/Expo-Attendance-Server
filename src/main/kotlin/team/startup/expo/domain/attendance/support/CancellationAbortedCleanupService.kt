package team.startup.expo.domain.attendance.support

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.global.config.CancellationAbortedRetentionProperties
import team.startup.expo.global.lock.AdvisoryJobLock
import java.time.Clock
import java.time.LocalDateTime

/**
 * 보관 기간(`cancellation-aborted-retention.days`)이 지난 중단된 취소 작업 기록을 지운다. 기본은 꺼져 있다(0).
 * 출석·입장·사전등록 회차 같은 데이터는 지우지 않는다. 인스턴스가 여러 개여도 한 번에 한 곳만 돌도록 어드바이저리 락을 잡는다.
 */
@Service
class CancellationAbortedCleanupService(
    private val cancellationGuardRepository: CancellationGuardRepository,
    private val retentionProperties: CancellationAbortedRetentionProperties,
    private val jobLock: AdvisoryJobLock,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 지운 건수를 돌려준다. 꺼져 있거나 다른 인스턴스가 처리 중이면 0이다. */
    fun cleanup(): Int {
        if (!retentionProperties.enabled) return 0
        val cutoff = LocalDateTime.now(clock).minusDays(retentionProperties.days.toLong())
        val deleted = jobLock.runIfAcquired(LOCK_KEY) { cancellationGuardRepository.deleteAbortedCreatedBefore(cutoff) }
        if (deleted == null) {
            logger.debug("다른 인스턴스가 중단된 취소 기록을 정리 중이라 건너뜁니다")
            return 0
        }
        if (deleted > 0) logger.info("보관 기간이 지난 중단된 취소 기록을 지웠습니다: 건수={}, 보관일={}", deleted, retentionProperties.days)
        return deleted
    }

    private companion object {
        const val LOCK_KEY = "cancellation-aborted-cleanup"
    }
}
