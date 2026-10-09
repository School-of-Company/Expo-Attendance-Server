package team.startup.expo.domain.attendance.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 박람회가 오늘 진행 중인지 확인한다. v1처럼 시작일과 종료일을 모두 포함한다. */
@Component
class ExpoPeriodValidator(
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) {
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

    /** 박람회의 시작일과 종료일(둘 다 포함). 없으면 404, 박람회 서비스가 응답하지 못하거나 날짜가 잘못이면 503이다. */
    fun period(expoId: String): Pair<LocalDate, LocalDate> {
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
}
