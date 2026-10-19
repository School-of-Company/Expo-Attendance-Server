package team.startup.expo.domain.attendance.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.config.PreregisterEntryProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 사전등록 참가자가 신청한 회차의 입장 시간인지 확인한다. 회차 기록이 없는 참가자(현장등록, 연동 전 등록)는 확인하지 않는다.
 * 신청 상태는 참가자 ID와 code가 맞는 사람에게만 알려야 하므로, 회차 기록이 있으면 먼저 `verifyCode`로 확인한 뒤에 검사한다.
 * 한 사람이 여러 회차에 배정될 수 있고(#160), 확정 상태인 회차 중 지금이 입장 시간인 회차로 입장한다.
 * 허용 시간은 회차 시작 `leadMinutes`분 전부터 종료 시각까지이고, 시각은 모두 `Instant`(UTC)로 비교한다.
 * 고른 회차 ID는 유저 서비스 입장 기록의 `preregisterSessionId`로 보낸다(#142).
 */
@Component
class PreregisterSessionValidator(
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val expoClient: ExpoClient,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    private val properties: PreregisterEntryProperties,
    private val clock: Clock,
) {
    /**
     * 취소된 신청을 거절한다. 회차 기록이 있는데 확정 상태인 회차가 하나도 없으면 취소된 신청이다.
     * [checkedSessionId]가 있으면(락 밖에서 [check]로 고른 회차) 그 회차가 아직 확정 상태여야 하고, 그 회차 ID를 돌려준다.
     * 전화번호 입장은 회차 시간과 무관하게 이것만 확인하고 회차를 붙이지 않는다(null). guard 공유 락 안에서 호출한다.
     */
    fun checkNotCancelled(
        expoId: String,
        participantId: Long,
        checkedSessionId: Long? = null,
    ): Long? {
        val records = preregisterSessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId)
        if (records.isEmpty()) return null
        val active = activeSessionIds(records)
        if (active.isEmpty() || (checkedSessionId != null && checkedSessionId !in active)) throw cancelled()
        return checkedSessionId
    }

    /**
     * 확정 상태인 회차 중 지금이 입장 시간인 회차 ID를 돌려준다. 입장 시간이 겹치면 시작이 이른 회차를 고른다.
     * 회차 기록이 없는 참가자는 확인하지 않고 null이다.
     */
    fun check(
        expoId: String,
        participantId: Long,
        verifyCode: () -> Unit,
    ): Long? {
        val records = preregisterSessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId)
        if (records.isEmpty()) return null
        verifyCode()
        val sessionIds = activeSessionIds(records)
        if (sessionIds.isEmpty()) throw cancelled()

        // 확정한 회차가 없어졌다는 404는 있을 수 없는 상태라 "없음"이 아니라 호출 실패(503)로 본다
        val sessions =
            sessionIds.map { sessionId ->
                val session = expoCircuitBreaker.callService("박람회") { expoClient.getPreregisterSession(expoId, sessionId) }
                Window(sessionId, session.startedAt.minus(Duration.ofMinutes(properties.leadMinutes)), session.startedAt, session.endedAt)
            }

        val now = clock.instant()
        val open = sessions.filter { !now.isBefore(it.opensAt) && !now.isAfter(it.endedAt) }
        open.minWithOrNull(compareBy<Window>({ it.startedAt }, { it.sessionId }))?.let { return it.sessionId }

        if (sessions.all { now.isAfter(it.endedAt) }) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "신청한 회차가 이미 끝났습니다.")
        }
        throw ExpectedException(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 ${properties.leadMinutes}분 전부터 입장할 수 있습니다.")
    }

    /** 확정 상태인 회차 ID. 확정 상태인데 회차가 없는 기록은 있을 수 없는 상태라 호출 실패(503)로 본다. */
    private fun activeSessionIds(records: List<PreregisterSession>): List<Long> =
        records
            .filter { it.status == PreregisterSessionStatus.ACTIVE }
            .map { it.sessionId ?: throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "신청한 회차를 확인할 수 없습니다.") }
            .distinct()
            .sorted()

    private fun cancelled() = ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")

    private data class Window(
        val sessionId: Long,
        val opensAt: Instant,
        val startedAt: Instant,
        val endedAt: Instant,
    )
}
