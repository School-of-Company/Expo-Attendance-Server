package team.startup.expo.domain.qr.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.qr.entity.DeletedExpo
import java.time.LocalDateTime

interface DeletedExpoRepository : JpaRepository<DeletedExpo, String> {
    /**
     * 박람회 삭제가 잡는 배타 트랜잭션 락이다. 트랜잭션이 끝나면 풀린다. [lockExpoShared]를 쥔 쓰기가 모두 끝나기를 기다리고,
     * 잡은 동안 새 쓰기는 기다린다. 삭제만 쓴다(#248).
     */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:expoId))) AS locked")
    fun lockExpo(
        @Param("expoId") expoId: String,
    ): Int

    /**
     * 삭제가 아닌 쓰기가 잡는, [lockExpo]와 같은 키의 공유 트랜잭션 락이다. 삭제와만 겹치지 않고 쓰기끼리는 서로 막지 않는다(#248).
     * 이 락을 잡은 뒤에 삭제 기록을 읽으므로 삭제가 먼저 커밋됐다면 반드시 보인다. 쓰기끼리의 순서가 필요하면 더 좁은 키로 따로 잡는다.
     */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock_shared(hashtext(:expoId))) AS locked")
    fun lockExpoShared(
        @Param("expoId") expoId: String,
    ): Int

    /** 이미 삭제 기록이 있으면 아무것도 하지 않는다. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = "INSERT INTO tb_deleted_expo (expo_id, deleted_at) VALUES (:expoId, :deletedAt) ON CONFLICT (expo_id) DO NOTHING",
    )
    fun insertIfAbsent(
        @Param("expoId") expoId: String,
        @Param("deletedAt") deletedAt: LocalDateTime,
    ): Int
}
