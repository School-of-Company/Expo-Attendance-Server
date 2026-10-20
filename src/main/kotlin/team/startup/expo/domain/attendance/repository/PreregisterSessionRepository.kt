package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.PreregisterSession
import java.time.LocalDateTime

/**
 * 참가자의 회차 기록은 (참가자, 회차)당 한 행이다(#160). revision은 행(회차)마다 비교한다.
 * 회차를 지정하지 않은 취소(기존 방식)는 참가자의 모든 회차를 취소하고, 회차 없는 취소 행에 그 revision을 남겨
 * 그보다 오래된 회차 PUT이 늦게 도착해도 새 회차를 만들지 못하게 한다.
 */
interface PreregisterSessionRepository : JpaRepository<PreregisterSession, Long> {
    fun findAllByExpoIdAndParticipantId(
        expoId: String,
        participantId: Long,
    ): List<PreregisterSession>

    /** 이 회차를 기록하거나(없으면 만들고) 확정 상태로 되돌린다. 다른 회차는 건드리지 않는다. 같은 요청을 다시 보내도 결과는 같다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, updated_at)
            VALUES (:expoId, :participantId, :sessionId, 'ACTIVE', :now)
            ON CONFLICT ON CONSTRAINT uk_preregister_session_participant_session
            DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
        """,
    )
    fun upsertActive(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("sessionId") sessionId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /**
     * revision이 이 회차에 이미 받은 값과 회차 없는 취소의 revision보다 클 때만 회차를 기록하고 확정 상태로 되돌린다.
     * 영향받은 행이 0이면 같거나 더 오래된 요청이다. 비교와 갱신이 한 문장이라 두 요청이 겹쳐도 더 큰 revision이 이긴다.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, revision, updated_at)
            SELECT :expoId, :participantId, :sessionId, 'ACTIVE', :revision, :now
            WHERE NOT EXISTS (
                SELECT 1 FROM tb_preregister_session
                WHERE expo_id = :expoId AND participant_id = :participantId AND session_id IS NULL AND revision >= :revision
            )
            ON CONFLICT ON CONSTRAINT uk_preregister_session_participant_session
            DO UPDATE SET status = 'ACTIVE', revision = EXCLUDED.revision, updated_at = EXCLUDED.updated_at
            WHERE tb_preregister_session.revision < EXCLUDED.revision
        """,
    )
    fun upsertActiveIfNewer(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("sessionId") sessionId: Long,
        @Param("revision") revision: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /** revision이 이 회차에 이미 받은 값보다 클 때만 이 회차를 취소로 바꾼다. 기록이 없으면 취소 기록을 만든다. 0이면 같거나 더 오래된 요청이다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, revision, updated_at)
            VALUES (:expoId, :participantId, :sessionId, 'CANCELLED', :revision, :now)
            ON CONFLICT ON CONSTRAINT uk_preregister_session_participant_session
            DO UPDATE SET status = 'CANCELLED', revision = EXCLUDED.revision, updated_at = EXCLUDED.updated_at
            WHERE tb_preregister_session.revision < EXCLUDED.revision
        """,
    )
    fun cancelSessionIfNewer(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("sessionId") sessionId: Long,
        @Param("revision") revision: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 회차를 지정하지 않은 취소: revision이 더 큰 회차만 모두 취소로 바꾼다(회차 없는 취소 행은 [recordCancelWithoutSessionIfNewer]가 맡는다). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_preregister_session SET status = 'CANCELLED', revision = :revision, updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId AND session_id IS NOT NULL AND revision < :revision
        """,
    )
    fun cancelAllSessionsIfNewer(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("revision") revision: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 회차를 지정하지 않은 취소의 revision을 회차 없는 취소 행에 남긴다(없으면 만들고, 더 클 때만 바꾼다). 0이면 같거나 더 오래된 요청이다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_preregister_session (expo_id, participant_id, session_id, status, revision, updated_at)
            VALUES (:expoId, :participantId, NULL, 'CANCELLED', :revision, :now)
            ON CONFLICT ON CONSTRAINT uk_preregister_session_participant_session
            DO UPDATE SET status = 'CANCELLED', revision = EXCLUDED.revision, updated_at = EXCLUDED.updated_at
            WHERE tb_preregister_session.revision < EXCLUDED.revision
        """,
    )
    fun recordCancelWithoutSessionIfNewer(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("revision") revision: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /**
     * 취소 뒤 현장등록으로 다시 등록했다(#249): revision이 더 큰 취소 기록만 현장 재등록으로 바꾼다. 확정 회차는 그대로 둔다.
     * 행과 revision을 남겨 그보다 오래된 회차 PUT·DELETE와 늦게 온 취소 보호 확정이 다시 막지 못하게 한다.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_preregister_session SET status = 'FIELD_REGISTERED', revision = :revision, updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId AND status = 'CANCELLED' AND revision < :revision
        """,
    )
    fun fieldRegisterIfNewer(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("revision") revision: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    /**
     * 이 회차의 기록이 있으면 취소로 바꾼다. 없으면 아무것도 하지 않는다. `revision`이 있으면 그보다 오래된 기록만 바꾸고 그 revision을 남긴다
     * (취소 보호 확정이 회차 취소 통지보다 먼저 와도 같은 결과가 되게 한다).
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_preregister_session
            SET status = 'CANCELLED', revision = COALESCE(:revision, revision), updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId AND session_id = :sessionId
              AND (CAST(:revision AS BIGINT) IS NULL OR revision < :revision)
        """,
    )
    fun cancelExistingSession(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("sessionId") sessionId: Long,
        @Param("revision") revision: Long?,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 기록이 있으면 모든 회차를 취소로 바꾼다. 없으면 아무것도 하지 않는다(기록이 없는 참가자는 회차 확인 대상이 아니다). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_preregister_session SET status = 'CANCELLED', updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId
        """,
    )
    fun cancelAll(
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
