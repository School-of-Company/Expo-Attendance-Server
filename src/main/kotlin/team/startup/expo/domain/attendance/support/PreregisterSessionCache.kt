package team.startup.expo.domain.attendance.support

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.config.PreregisterSessionCacheProperties
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * 박람회 서비스에서 읽은 회차 시간을 `(expoId, sessionId)` 키로 `preregister-session-cache.ttl-seconds` 동안 기억한다(#249).
 * 스캔 하나가 같은 회차를 세 번 읽고 그중 둘은 게이트 트랜잭션 안(커넥션을 쥔 채)이라, 원격 호출 수가 곧 커넥션 점유 시간이다.
 * 프로젝션에서 읽은 값은 여기를 거치지 않는다.
 *
 * - 같은 키를 동시에 읽으면 원격 호출은 한 번만 하고 나머지는 그 결과를 기다린다(single-flight).
 *   비동기 캐시라 원격 호출 중에도 다른 키의 조회가 막히지 않고, 호출은 [DIRECT] 실행기로 호출한 스레드에서 한다.
 * - 실패(404 포함)는 기억하지 않고, 호출한 쪽과 기다린 쪽 모두에게 같은 예외를 던진다. 다음 조회는 다시 부른다.
 * - 트레이드오프: 회차 시간이 바뀌면 최대 TTL만큼 이전 시간으로 입장을 판정한다(박람회 기간 캐시와 같다).
 * - 항목은 [MAX_CACHED_SESSIONS]개까지이고, 넘치면 Caffeine이 덜 쓰인 항목부터 비운다.
 */
@Component
class PreregisterSessionCache(
    private val properties: PreregisterSessionCacheProperties,
    clock: Clock,
) {
    private val cache: AsyncCache<Pair<String, Long>, PreregisterSessionResDto> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(maxOf(1, properties.ttlSeconds)))
            .maximumSize(MAX_CACHED_SESSIONS)
            .executor(DIRECT)
            .ticker(Ticker { TimeUnit.MILLISECONDS.toNanos(clock.millis()) })
            .buildAsync()

    fun get(
        expoId: String,
        sessionId: Long,
        load: () -> PreregisterSessionResDto,
    ): PreregisterSessionResDto {
        if (properties.ttlSeconds <= 0) return load()
        try {
            return cache.get(expoId to sessionId, Function { load() }).join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }
    }

    private companion object {
        const val MAX_CACHED_SESSIONS = 10_000L
        val DIRECT = Executor { it.run() }
    }
}
