package team.startup.expo.domain.attendance.support

import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterSessionCacheProperties
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/**
 * 박람회 서비스에서 읽은 회차 시간을 `(expoId, sessionId)` 키로 `preregister-session-cache.ttl-seconds` 동안 기억한다(#249).
 * 스캔 하나가 같은 회차를 세 번 읽고 그중 둘은 게이트 트랜잭션 안(커넥션을 쥔 채)이라, 원격 호출 수가 곧 커넥션 점유 시간이다.
 * 프로젝션에서 읽은 값은 여기를 거치지 않는다.
 *
 * - 같은 키를 동시에 읽으면 원격 호출은 한 번만 하고 나머지는 그 결과를 기다린다(single-flight).
 * - 실패(404 포함)는 기억하지 않고, 호출한 쪽과 기다린 쪽 모두에게 같은 예외를 던진다. 다음 조회는 다시 부른다.
 * - 트레이드오프: 회차 시간이 바뀌면 최대 TTL만큼 이전 시간으로 입장을 판정한다(박람회 기간 캐시와 같다).
 * - 항목은 [MAX_CACHED_SESSIONS]개까지이고, 가득 차면 만료된 항목을 비운다. 그래도 가득 차면 기억하지 않고 바로 부른다.
 */
@Component
class PreregisterSessionCache(
    private val properties: PreregisterSessionCacheProperties,
    private val clock: Clock,
) {
    private class Entry(
        val future: CompletableFuture<PreregisterSessionResDto> = CompletableFuture(),
        @Volatile var expiresAt: Instant = Instant.MAX,
    )

    private val cache = ConcurrentHashMap<Pair<String, Long>, Entry>()

    fun get(
        expoId: String,
        sessionId: Long,
        load: () -> PreregisterSessionResDto,
    ): PreregisterSessionResDto {
        if (properties.ttlSeconds <= 0) return load()
        val key = expoId to sessionId
        while (true) {
            val now = clock.instant()
            val existing = cache[key]
            if (existing == null) {
                if (cache.size >= MAX_CACHED_SESSIONS) {
                    cache.entries.removeIf { it.value.future.isDone && !it.value.expiresAt.isAfter(now) }
                    if (cache.size >= MAX_CACHED_SESSIONS) return load()
                }
                val mine = Entry()
                if (cache.putIfAbsent(key, mine) == null) return fetch(key, mine, now, load)
                continue
            }
            if (existing.future.isDone && (existing.future.isCompletedExceptionally || !existing.expiresAt.isAfter(now))) {
                cache.remove(key, existing)
                continue
            }
            try {
                return existing.future.join()
            } catch (e: CompletionException) {
                throw e.cause ?: e
            }
        }
    }

    private fun fetch(
        key: Pair<String, Long>,
        entry: Entry,
        now: Instant,
        load: () -> PreregisterSessionResDto,
    ): PreregisterSessionResDto {
        try {
            val loaded = load()
            entry.expiresAt = now.plusSeconds(properties.ttlSeconds)
            entry.future.complete(loaded)
            return loaded
        } catch (e: Throwable) {
            cache.remove(key, entry)
            entry.future.completeExceptionally(e)
            throw e
        }
    }

    private companion object {
        const val MAX_CACHED_SESSIONS = 10_000
    }
}
