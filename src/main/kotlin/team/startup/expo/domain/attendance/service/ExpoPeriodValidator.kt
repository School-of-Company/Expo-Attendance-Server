package team.startup.expo.domain.attendance.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.config.ExpoPeriodCacheProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

/** 박람회가 오늘 진행 중인지 확인한다. v1처럼 시작일과 종료일을 모두 포함한다. */
@Component
class ExpoPeriodValidator(
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val properties: ExpoPeriodCacheProperties,
    private val clock: Clock,
) {
    private class CachedPeriod(
        val period: Pair<LocalDate, LocalDate>,
        val expiresAt: Instant,
    )

    private val cache = ConcurrentHashMap<String, CachedPeriod>()

    /** @param today 호출한 쪽이 이미 읽은 오늘 날짜. 같은 요청 안에서 날짜가 갈리지 않게 넘긴다. */
    fun checkInProgress(
        expoId: String,
        today: LocalDate = LocalDate.now(clock),
    ) {
        val (startedDay, finishedDay) = period(expoId)
        if (today < startedDay || today > finishedDay) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.")
        }
    }

    /**
     * 박람회의 시작일과 종료일(둘 다 포함). 없으면 404, 박람회 서비스가 응답하지 못하거나 날짜가 잘못이면 503이다.
     * 입구 스캔마다 박람회 서비스를 부르지 않도록 성공한 응답만 `expo-period-cache.ttl-seconds` 동안 기억한다(없음·실패는 기억하지 않는다).
     */
    fun period(expoId: String): Pair<LocalDate, LocalDate> {
        val now = clock.instant()
        cache[expoId]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.period }

        val fetched = fetchPeriod(expoId)
        if (properties.ttlSeconds > 0) {
            if (cache.size >= MAX_CACHED_EXPOS) cache.entries.removeIf { !it.value.expiresAt.isAfter(now) }
            if (cache.size < MAX_CACHED_EXPOS) cache[expoId] = CachedPeriod(fetched, now.plusSeconds(properties.ttlSeconds))
        }
        return fetched
    }

    private fun fetchPeriod(expoId: String): Pair<LocalDate, LocalDate> {
        val period =
            expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")) {
                expoClient.getPeriod(expoId)
            }
        // 박람회 서비스가 날짜를 잘못 주면 호출 실패와 같이 503으로 알린다(500이 아니라)
        return try {
            LocalDate.parse(period.startedDay) to LocalDate.parse(period.finishedDay)
        } catch (_: DateTimeParseException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.")
        }
    }

    private companion object {
        const val MAX_CACHED_EXPOS = 1_000
    }
}
