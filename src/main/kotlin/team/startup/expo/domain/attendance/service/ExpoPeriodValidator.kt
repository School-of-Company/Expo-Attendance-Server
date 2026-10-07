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

/** 박람회가 오늘 진행 중인지 확인한다. v1처럼 시작일과 종료일을 모두 포함한다. */
@Component
class ExpoPeriodValidator(
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) {
    fun checkInProgress(expoId: String) {
        val period =
            expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")) {
                expoClient.getPeriod(expoId)
            }
        val today = LocalDate.now(clock)
        if (today < LocalDate.parse(period.startedDay) || today > LocalDate.parse(period.finishedDay)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.")
        }
    }
}
