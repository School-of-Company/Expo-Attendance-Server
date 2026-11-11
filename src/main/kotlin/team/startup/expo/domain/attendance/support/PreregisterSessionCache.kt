package team.startup.expo.domain.attendance.support

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterSessionCacheProperties
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * 박람회 서비스에서 읽은 회차 시간을 `(expoId, sessionId)` 키로 `preregister-session-cache.ttl-seconds` 동안 기억한다(#249).
 * 스캔 하나가 같은 회차를 세 번 읽고 그중 둘은 게이트 트랜잭션 안(커넥션을 쥔 채)이라, 원격 호출 수가 곧 커넥션 점유 시간이다.
 * 프로젝션에서 읽은 값은 여기를 거치지 않는다.
 *
 * - 같은 키를 동시에 읽으면 원격 호출은 한 번만 하고 나머지는 그 결과를 기다린다(single-flight). 먼저 빈 future로 자리를 잡은
 *   스레드만 [get]의 `load`를 부르고, 호출은 캐시 락 밖에서 하므로 다른 키의 조회는 막히지 않는다.
 * - 실패(404 포함)는 기억하지 않고, 호출한 쪽과 기다린 쪽 모두에게 같은 예외를 던진다. 다음 조회는 다시 부른다.
 * - TTL은 자리를 잡은 때부터 센다. 회차 시간이 바뀌면 최대 TTL만큼 이전 시간으로 입장을 판정한다(박람회 기간 캐시와 같다).
 * - 항목은 [MAX_CACHED_SESSIONS]개까지이고, 넘치면 Caffeine이 덜 쓰인 항목부터 비운다.
 */
@Component
class PreregisterSessionCache internal constructor(
    private val properties: PreregisterSessionCacheProperties,
    ticker: Ticker,
) {
    /** 운영은 단조 시계([Ticker.systemTicker])를 쓴다. 벽시계는 NTP 보정으로 뒤로 갈 수 있다. */
    @Autowired
    constructor(properties: PreregisterSessionCacheProperties) : this(properties, Ticker.systemTicker())

    private val cache: Cache<Pair<String, Long>, CompletableFuture<PreregisterSessionResDto>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(maxOf(1, properties.ttlSeconds)))
            .maximumSize(MAX_CACHED_SESSIONS)
            .ticker(ticker)
            .build()

    fun get(
        expoId: String,
        sessionId: Long,
        load: () -> PreregisterSessionResDto,
    ): PreregisterSessionResDto {
        if (properties.ttlSeconds <= 0) return load()
        val key = expoId to sessionId
        val mine = CompletableFuture<PreregisterSessionResDto>()
        val existing = cache.asMap().putIfAbsent(key, mine)
        if (existing != null) return await(existing)
        try {
            mine.complete(load())
        } catch (e: Throwable) {
            cache.asMap().remove(key, mine)
            mine.completeExceptionally(e)
            throw e
        }
        return mine.join()
    }

    private fun await(future: CompletableFuture<PreregisterSessionResDto>): PreregisterSessionResDto =
        try {
            future.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }

    private companion object {
        const val MAX_CACHED_SESSIONS = 10_000L
    }
}
