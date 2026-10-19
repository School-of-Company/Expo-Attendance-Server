package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.WalkInLog
import java.time.LocalDate
import java.time.LocalDateTime

interface WalkInLogRepository : JpaRepository<WalkInLog, Long> {
    /** 같은 박람회·날짜·구분의 증감을 한 줄로 세우는 트랜잭션 락이다. 트랜잭션이 끝나면 풀린다. */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS locked")
    fun lock(
        @Param("key") key: String,
    ): Int

    @Query(nativeQuery = true, value = "SELECT EXISTS (SELECT 1 FROM tb_walk_in_log WHERE expo_id = :expoId AND request_id = :requestId)")
    fun existsRequest(
        @Param("expoId") expoId: String,
        @Param("requestId") requestId: String,
    ): Boolean

    @Query(
        nativeQuery = true,
        value = """
            SELECT COALESCE(SUM(delta), 0) FROM tb_walk_in_log
            WHERE expo_id = :expoId AND attendance_date = :attendanceDate AND category = :category
        """,
    )
    fun count(
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("category") category: String,
    ): Long

    /** 구분별 합계. 기록이 없는 구분은 행이 없다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT category, COALESCE(SUM(delta), 0) FROM tb_walk_in_log
            WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to GROUP BY category
        """,
    )
    fun countsByCategory(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<Array<Any>>

    /** 날짜·구분별 [날짜, 구분, 순증감](#150). 기록이 없는 날짜·구분은 행이 없다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT attendance_date, category, COALESCE(SUM(delta), 0) FROM tb_walk_in_log
            WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
            GROUP BY attendance_date, category ORDER BY attendance_date, category
        """,
    )
    fun sumByDateAndCategory(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<Array<Any?>>

    /** 이 시각 이후의 간편입장 순증감. */
    @Query(
        nativeQuery = true,
        value = "SELECT COALESCE(SUM(delta), 0) FROM tb_walk_in_log WHERE expo_id = :expoId AND created_at >= :since",
    )
    fun sumDeltaSince(
        @Param("expoId") expoId: String,
        @Param("since") since: LocalDateTime,
    ): Long

    /** 같은 박람회에 같은 `request_id`가 이미 있으면 아무것도 하지 않는다(0). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_walk_in_log (expo_id, attendance_date, category, delta, request_id, created_at, scanner)
            VALUES (:expoId, :attendanceDate, :category, :delta, :requestId, :createdAt, :scanner)
            ON CONFLICT (expo_id, request_id) DO NOTHING
        """,
    )
    fun insert(
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("category") category: String,
        @Param("delta") delta: Int,
        @Param("requestId") requestId: String?,
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("scanner") scanner: String?,
    ): Int

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_walk_in_log WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
