package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.CancellationGuard
import java.time.LocalDateTime

interface CancellationGuardRepository : JpaRepository<CancellationGuard, Long> {
    fun findByExpoIdAndParticipantId(
        expoId: String,
        participantId: Long,
    ): CancellationGuard?

    fun findByExpoIdAndTraineeId(
        expoId: String,
        traineeId: Long,
    ): CancellationGuard?

    /** 이 트랜잭션에서 락을 기다리는 시간의 상한을 둔다. 넘기면 락 오류로 끝나 스캔이 한없이 매달리지 않는다. */
    @Query(nativeQuery = true, value = "SELECT set_config('lock_timeout', :timeout, true)")
    fun setLockTimeout(
        @Param("timeout") timeout: String,
    ): String

    /** 스캔이 잡는 공유 락이다. 같은 키의 배타 락(취소 검사)과는 서로 기다리고, 스캔끼리는 막지 않는다. 트랜잭션이 끝나면 풀린다. */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock_shared(hashtextextended(:key, 0))) AS locked")
    fun lockShared(
        @Param("key") key: String,
    ): Int

    /** 취소 검사가 잡는 배타 락이다. 진행 중인 스캔이 끝나기를 기다린다. */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS locked")
    fun lockExclusive(
        @Param("key") key: String,
    ): Int

    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_cancellation_guard (expo_id, participant_id, operation_id, state, created_at, updated_at)
            VALUES (:expoId, :participantId, :operationId, 'HELD', :now, :now)
            ON CONFLICT (expo_id, participant_id) DO NOTHING
        """,
    )
    fun insertHeldIfAbsent(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("operationId") operationId: String,
        @Param("now") now: LocalDateTime,
    ): Int

    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_cancellation_guard SET trainee_id = :traineeId, updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId AND operation_id = :operationId
        """,
    )
    fun setTrainee(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("operationId") operationId: String,
        @Param("traineeId") traineeId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_cancellation_guard SET state = 'COMMITTED', updated_at = :now
            WHERE expo_id = :expoId AND participant_id = :participantId AND operation_id = :operationId AND state = 'HELD'
        """,
    )
    fun commit(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("operationId") operationId: String,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 이 작업이 건 보호를 푼다(취소가 거절되었거나 중단된 경우). 확정된 보호는 지우지 않는다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            DELETE FROM tb_cancellation_guard
            WHERE expo_id = :expoId AND participant_id = :participantId AND operation_id = :operationId AND state = 'HELD'
        """,
    )
    fun deleteHeld(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("operationId") operationId: String,
    ): Int

    /** 재신청으로 회차가 다시 기록되면 확정된 취소 보호를 지운다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = "DELETE FROM tb_cancellation_guard WHERE expo_id = :expoId AND participant_id = :participantId AND state = 'COMMITTED'",
    )
    fun deleteCommitted(
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
    ): Int

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_cancellation_guard WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
