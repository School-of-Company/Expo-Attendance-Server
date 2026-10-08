package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.PreregisterSession
import java.time.LocalDateTime

interface PreregisterSessionRepository : JpaRepository<PreregisterSession, Long> {
    fun findByExpoIdAndParticipantId(
        expoId: String,
        participantId: Long,
    ): PreregisterSession?

    /** 회차를 기록하거나(없으면 만들고, 있으면 바꾸고) 확정 상태로 되돌린다. 같은 요청을 다시 보내도 결과는 같다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, updated_at)
            VALUES (:expoId, :participantId, :sessionId, 'ACTIVE', :now)
            ON CONFLICT (expo_id, participant_id)
            DO UPDATE SET session_id = EXCLUDED.session_id, status = 'ACTIVE', updated_at = EXCLUDED.updated_at
        """,
    )
    fun upsertActive(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("sessionId") sessionId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 기록이 있으면 취소로 바꾼다. 없으면 아무것도 하지 않는다(기록이 없는 참가자는 회차 확인 대상이 아니다). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_preregister_session SET status = 'CANCELLED', updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId
        """,
    )
    fun cancel(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_preregister_session WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
