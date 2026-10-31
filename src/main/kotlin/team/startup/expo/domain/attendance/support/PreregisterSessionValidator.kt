package team.startup.expo.domain.attendance.support

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.entity.PreregisterSession
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.projection.ExpoProjectionReader
import team.startup.expo.global.config.PreregisterEntryProperties
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 오늘 ACTIVE FIELD가 없으면 사전등록 회차의 입장 시간을 확인한다. 날짜를 알 수 없는 과거 FIELD는 권한이 아니다.
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
    private val fieldVisits: FieldVisitRepository,
    private val expoProjection: ExpoProjectionReader,
) {
    /**
     * 취소된 신청을 거절한다. 회차 기록이 있는데 확정 상태인 회차가 하나도 없으면 취소된 신청이다.
     * [checkedSessionId]가 있으면(락 밖에서 [check]로 고른 회차) 그 회차가 아직 확정 상태여야 하고, 그 회차 ID를 돌려준다.
     * 전화번호 입장은 시간검사 없이 오늘 시작이 가장 이른 회차를 고른다. guard 공유 락 안에서 호출한다.
     */
    fun checkNotCancelled(
        expoId: String,
        participantId: Long,
        checkedSessionId: Long? = null,
        attendanceDate: LocalDate? = null,
    ): Long? {
        val date = attendanceDate ?: LocalDate.now(clock)
        val records = records(expoId, participantId)
        if (checkedSessionId == null && activeField(expoId, participantId, date) != null) return null
        val active = activeSessionIds(records)
        if (active.isEmpty() || (checkedSessionId != null && checkedSessionId !in active)) throw cancelled()
        val today = todaySessions(expoId, records, date)
        if (today.isEmpty() || (checkedSessionId != null && today.none { it.id == checkedSessionId })) throw cancelled()
        return checkedSessionId ?: today.minWith(compareBy({ it.startedAt }, { it.id })).id
    }

    /**
     * QR 조회 자격이 없는지 판단한다. 확정 상태인 PRE 회차가 하나라도 있으면 방문일 전에도 조회할 수 있고, FIELD는 오늘 ACTIVE일 때만 된다.
     * QR을 보여 주는 것과 입장을 허용하는 것은 별개라서 방문일·회차 입장 시간은 여기서 보지 않는다. 그건 스캔의 [check]·[checkNotCancelled]·[checkToday]가 한다.
     */
    fun isCancelled(
        expoId: String,
        participantId: Long,
    ): Boolean {
        if (activeField(expoId, participantId) != null) return false
        return records(expoId, participantId).none { it.status == PreregisterSessionStatus.ACTIVE }
    }

    /**
     * 확정 상태인 회차 중 지금이 입장 시간인 회차 ID를 돌려준다. 입장 시간이 겹치면 시작이 이른 회차를 고른다.
     * 오늘 ACTIVE FIELD는 회차 없이 통과한다.
     * 열린 회차가 없을 때 [occupation]이 교원·예비교사이면 오늘 확정 회차 중 시작이 가장 이른 회차로 시간과 관계없이 입장한다(#283).
     * [occupation]은 코드를 확인한 뒤에만, 열린 회차가 없을 때만 부른다.
     */
    fun check(
        expoId: String,
        participantId: Long,
        occupation: () -> String? = { null },
        verifyCode: () -> Unit,
    ): Long? {
        val records = records(expoId, participantId)
        verifyCode()
        if (activeField(expoId, participantId) != null) return null
        val sessionIds = activeSessionIds(records)
        if (sessionIds.isEmpty()) throw cancelled()

        // 확정한 회차가 없어졌다는 404는 있을 수 없는 상태라 "없음"이 아니라 호출 실패(503)로 본다
        val sessions =
            sessionIds.map { sessionId ->
                val session = session(expoId, sessionId)
                Window(sessionId, session.startedAt.minus(Duration.ofMinutes(properties.leadMinutes)), session.startedAt, session.endedAt)
            }

        val now = clock.instant()
        val open = sessions.filter { !now.isBefore(it.opensAt) && !now.isAfter(it.endedAt) }
        open.minWithOrNull(compareBy<Window>({ it.startedAt }, { it.sessionId }))?.let { return it.sessionId }

        val today = LocalDate.now(clock)
        val todaySessions = sessions.filter { it.startedAt.atZone(clock.zone).toLocalDate() == today }
        if (todaySessions.isNotEmpty() && occupation() in ALL_DAY_OCCUPATIONS) {
            return todaySessions.minWith(compareBy<Window>({ it.startedAt }, { it.sessionId })).sessionId
        }

        if (sessions.all { now.isAfter(it.endedAt) }) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "신청한 회차가 이미 끝났습니다.")
        }
        throw ExpectedException(HttpStatus.BAD_REQUEST, "입장 시간이 아닙니다. 신청한 회차 시작 ${properties.leadMinutes}분 전부터 입장할 수 있습니다.")
    }

    /** 현장 재등록으로 끝난 기록은 회차 기록이 없는 것과 같다. */
    private fun records(
        expoId: String,
        participantId: Long,
    ) = preregisterSessionRepository
        .findAllByExpoIdAndParticipantId(expoId, participantId)
        .filter { it.status != PreregisterSessionStatus.FIELD_REGISTERED }

    /** 확정 상태인 회차 ID. 확정 상태인데 회차가 없는 기록은 있을 수 없는 상태라 호출 실패(503)로 본다. */
    private fun activeSessionIds(records: List<PreregisterSession>): List<Long> =
        records
            .filter { it.status == PreregisterSessionStatus.ACTIVE }
            .map { it.sessionId ?: throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "신청한 회차를 확인할 수 없습니다.") }
            .distinct()
            .sorted()

    private fun cancelled() = ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")

    fun activeField(
        expoId: String,
        participantId: Long,
        attendanceDate: LocalDate? = null,
    ) = fieldVisits.visits(expoId, participantId).firstOrNull {
        it.visitDate == (attendanceDate ?: LocalDate.now(clock)) && it.status == FieldVisitStatus.ACTIVE
    }

    fun checkToday(
        expoId: String,
        participantId: Long,
        attendanceDate: LocalDate? = null,
    ) {
        val date = attendanceDate ?: LocalDate.now(clock)
        if (activeField(expoId, participantId, date) != null) return
        if (todaySessions(expoId, records(expoId, participantId), date).isEmpty()) throw cancelled()
    }

    private fun todaySessions(
        expoId: String,
        records: List<PreregisterSession>,
        attendanceDate: LocalDate = LocalDate.now(clock),
    ) = activeSessionIds(records)
        .map { sessionId -> session(expoId, sessionId) }
        .filter { it.startedAt.atZone(clock.zone).toLocalDate() == attendanceDate }

    /** 회차 시간. 프로젝션이 꺼져 있거나 없으면 박람회 서비스를 부른다(#240). */
    private fun session(
        expoId: String,
        sessionId: Long,
    ): PreregisterSessionResDto =
        expoProjection.preregisterSession(expoId, sessionId)
            ?: expoCircuitBreaker.callService("박람회") { expoClient.getPreregisterSession(expoId, sessionId) }

    private companion object {
        // 신청 회차 시간과 관계없이 당일 입장하는 직업(#283)
        val ALL_DAY_OCCUPATIONS = setOf("TEACHER", "PRE_SERVICE_TEACHER")
    }

    private data class Window(
        val sessionId: Long,
        val opensAt: Instant,
        val startedAt: Instant,
        val endedAt: Instant,
    )
}
