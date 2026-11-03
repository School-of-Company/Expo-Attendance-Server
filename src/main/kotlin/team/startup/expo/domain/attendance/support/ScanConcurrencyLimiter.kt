package team.startup.expo.domain.attendance.support

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.config.ScanConcurrencyProperties
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 취소 보호 게이트([CancellationGuardGate])에 들어가는 동시 스캔 수를 제한한다(#247). 게이트 트랜잭션은 커넥션 하나를 쥔 채 안에서
 * fence 쓰기(`REQUIRES_NEW`)로 하나를 더 요구한다. 동시 스캔이 풀 크기에 닿으면 모두 첫 커넥션을 쥔 채 두 번째를 기다리는 교착이 되고
 * 풀 타임아웃으로만 풀린다. 허용 수를 `풀 크기 - reserve`로 막으면 최소 reserve개가 항상 남아 중첩 요청이 진행되므로 교착이 생기지 않는다.
 *
 * 자리는 커넥션을 잡기 전에 얻는다. 못 얻으면 DB와 다른 서비스에 아무것도 쓰기 전에 거절한다.
 */
@Component
class ScanConcurrencyLimiter(
    properties: ScanConcurrencyProperties,
    @Value("\${spring.datasource.hikari.maximum-pool-size:30}") poolSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val waitMs = properties.waitMs
    val permits = permitsFor(poolSize, properties.reserve)
    private val semaphore = Semaphore(permits)

    init {
        // reserve가 0 이하이면 허용 수가 풀 크기 이상이 되어 막으려던 교착이 다시 생긴다
        require(properties.reserve >= 1) { "scan-concurrency.reserve는 1 이상이어야 합니다: ${properties.reserve}" }
        if (poolSize - properties.reserve < 2) {
            log.warn(
                "동시 스캔 허용 수가 {}개뿐입니다(DB 풀 {}, reserve {}). DB_POOL_SIZE를 늘리거나 SCAN_CONCURRENCY_RESERVE를 줄이세요.",
                permits,
                poolSize,
                properties.reserve,
            )
        }
    }

    fun <T> limited(block: () -> T): T {
        if (!semaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) {
            AttendanceMetrics.recordScanRejected()
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 잠시 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }

    companion object {
        /** 풀 크기에서 reserve를 뺀 허용 수. 풀이 작아도 최소 1개는 허용한다. */
        fun permitsFor(
            poolSize: Int,
            reserve: Int,
        ) = maxOf(1, poolSize - reserve)
    }
}
