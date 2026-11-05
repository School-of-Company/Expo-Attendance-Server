package team.startup.expo.domain.attendance.support

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import team.startup.expo.global.config.AdminStatsCacheProperties
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * 입·퇴장 현황(summary·hourly·recent·scanners) 조회 결과를 `admin-stats-cache.ttl-millis`(기본 1초) 동안 대시보드끼리 함께 쓴다(#255).
 * 변경 신호를 받으면 대시보드가 한꺼번에 다시 읽는데, 한 번의 조회가 그날 행을 모두 읽는 집계라 DB 부하가 대시보드 수에 비례했다.
 *
 * - 키는 (조회 종류, 박람회, 요청 조건)이다. 같은 키를 동시에 읽으면 한 번만 계산하고 나머지는 그 결과를 기다린다(single-flight).
 *   먼저 빈 future로 자리를 잡은 스레드만 계산하고, 계산은 캐시 락 밖에서 하므로 다른 키는 막히지 않는다.
 * - 실패는 기억하지 않고, 계산한 쪽과 기다린 쪽 모두에게 같은 예외를 던진다.
 * - [invalidate]는 그 박람회의 항목을 계산 중인 것까지 버린다. 버린 뒤의 조회는 새로 계산하므로, 변경 신호 뒤의 재조회가 신호 전에 시작한
 *   계산 결과를 받지 않는다. 변경 신호를 받는 [AdminDataChangeListener]가 SSE를 보내기 전에 부른다.
 * - 컨트롤러에서 감싸 캐시에 있으면 트랜잭션과 DB 커넥션을 열지 않는다.
 */
@Component
class AdminStatsCache internal constructor(
    private val properties: AdminStatsCacheProperties,
    ticker: Ticker,
) {
    @Autowired
    constructor(properties: AdminStatsCacheProperties) : this(properties, Ticker.systemTicker())

    private val cache: Cache<Key, CompletableFuture<Any>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofMillis(maxOf(1, properties.ttlMillis)))
            .maximumSize(MAX_ENTRIES)
            .ticker(ticker)
            .build()

    fun <T : Any> get(
        kind: String,
        expoId: String,
        request: Any,
        load: () -> T,
    ): T {
        if (properties.ttlMillis <= 0) return load()
        val key = Key(kind, expoId, request)
        val mine = CompletableFuture<Any>()
        val existing = cache.asMap().putIfAbsent(key, mine)
        if (existing != null) return cast(await(existing))
        try {
            val value = load()
            mine.complete(value)
            return value
        } catch (e: Throwable) {
            cache.asMap().remove(key, mine)
            mine.completeExceptionally(e)
            throw e
        }
    }

    /** 그 박람회의 결과를 모두 버린다. */
    fun invalidate(expoId: String) {
        cache.asMap().keys.removeIf { it.expoId == expoId }
    }

    /** 변경 신호를 잃었을 수 있을 때(LISTEN 재연결) 모두 버린다. */
    fun invalidateAll() {
        cache.invalidateAll()
    }

    /** 같은 키(같은 조회 종류)에는 같은 타입의 결과만 들어간다. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> cast(value: Any): T = value as T

    private fun await(future: CompletableFuture<Any>): Any =
        try {
            future.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }

    private data class Key(
        val kind: String,
        val expoId: String,
        val request: Any,
    )

    private companion object {
        const val MAX_ENTRIES = 1_000L
    }
}
