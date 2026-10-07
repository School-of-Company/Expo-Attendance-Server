package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.DeletedProgram
import java.time.LocalDateTime

interface DeletedProgramRepository : JpaRepository<DeletedProgram, String> {
    /**
     * 프로그램 하나에 대한 출석 기록과 삭제를 한 줄로 세우는 트랜잭션 락을 건다. 트랜잭션이 끝나면 풀린다.
     * 이 락을 잡은 뒤에 삭제 기록을 읽으므로 삭제가 먼저 커밋됐다면 반드시 보인다.
     */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:programKey))) AS locked")
    fun lockProgram(
        @Param("programKey") programKey: String,
    ): Int

    /** 이미 삭제 기록이 있으면 아무것도 하지 않는다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_deleted_program (program_key, deleted_at) VALUES (:programKey, :deletedAt)
            ON CONFLICT (program_key) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("programKey") programKey: String,
        @Param("deletedAt") deletedAt: LocalDateTime,
    ): Int
}
