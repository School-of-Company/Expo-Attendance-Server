package team.startup.expo.domain.attendance.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.config.PreregisterEntryProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Duration

/**
 * 사전등록 참가자가 신청한 회차의 입장 시간인지 확인한다. 회차 기록이 없는 참가자(현장등록, 연동 전 등록)는 확인하지 않는다.
 * 신청 상태는 참가자 ID와 code가 맞는 사람에게만 알려야 하므로, 회차 기록이 있으면 먼저 `verifyCode`로 확인한 뒤에 검사한다.
 * 허용 시간은 회차 시작 `leadMinutes`분 전부터 종료 시각까지이고, 시각은 모두 `Instant`(UTC)로 비교한다.
 */
@Component
class PreregisterSessionValidator(
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val properties: PreregisterEntryProperties,
    private val clock: Clock,
) {
    fun check(
        expoId: String,
        participantId: Long,
        verifyCode: () -> Unit,
    ) {
        val assignment = preregisterSessionRepository.findByExpoIdAndParticipantId(expoId, participantId) ?: return
        verifyCode()
        if (assignment.status == PreregisterSessionStatus.CANCELLED) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청의 QR입니다.")
        }

        // 회차가 없어졌다는 404는 있을 수 없는 상태라 "없음"이 아니라 호출 실패(503)로 본다
        val session =
            expoCircuitBreaker.callService("박람회") { expoClient.getPreregisterSession(expoId, assignment.sessionId) }

        val now = clock.instant()
        val opensAt = session.startedAt.minus(Duration.ofMinutes(properties.leadMinutes))
        if (now.isBefore(opensAt)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 ${properties.leadMinutes}분 전부터 입장할 수 있습니다.")
        }
        if (now.isAfter(session.endedAt)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "신청한 회차가 이미 끝났습니다.")
        }
    }
}
