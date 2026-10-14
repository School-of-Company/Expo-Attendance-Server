package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import java.time.LocalDate
import java.time.LocalDateTime

interface EntryLogRepository : JpaRepository<EntryLog, Long> {
    fun findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: String,
        attendanceDate: LocalDate,
    ): EntryLog?

    /** 오늘 입장 로그를 남긴다. 이미 있으면 아무것도 하지 않는다(같은 날 한 번만 입장하므로 하루 한 행). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, category, scanner)
            VALUES (:expoId, :subjectType, :subjectId, :attendanceDate, :enteredAt, :category, :scanner)
            ON CONFLICT (expo_id, subject_type, subject_id, attendance_date) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("expoId") expoId: String,
        @Param("subjectType") subjectType: String,
        @Param("subjectId") subjectId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("enteredAt") enteredAt: LocalDateTime,
        @Param("category") category: String?,
        @Param("scanner") scanner: String?,
    ): Int

    /** 오늘 입장했고 아직 퇴장하지 않은 경우에만 퇴장 시각을 기록한다. 0이면 입장 기록이 없거나 이미 퇴장했다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_entry_log SET exited_at = :exitedAt, exit_scanner = :exitScanner
            WHERE expo_id = :expoId AND subject_type = :subjectType AND subject_id = :subjectId
              AND attendance_date = :attendanceDate AND exited_at IS NULL
        """,
    )
    fun markExited(
        @Param("expoId") expoId: String,
        @Param("subjectType") subjectType: String,
        @Param("subjectId") subjectId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("exitedAt") exitedAt: LocalDateTime,
        @Param("exitScanner") exitScanner: String?,
    ): Int

    /** 종류·구분별 [종류, 구분, 입장 수, 퇴장 수]. 구분은 종이 QR에만 있다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT subject_type, category, COUNT(*), COUNT(exited_at) FROM tb_entry_log
            WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to GROUP BY subject_type, category
        """,
    )
    fun summarize(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<Array<Any?>>

    /** 기간 안에서 서로 다른 QR(참가자·연수자·종이 QR) 수. 같은 QR이 다른 날 다시 입장해도 한 번만 센다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT subject_type, subject_id FROM tb_entry_log
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
            ) s
        """,
    )
    fun countDistinctSubjects(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): Long

    /** 이 시각 이후의 QR 입장 수. */
    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM tb_entry_log WHERE expo_id = :expoId AND entered_at >= :since")
    fun countEnteredSince(
        @Param("expoId") expoId: String,
        @Param("since") since: LocalDateTime,
    ): Long

    /**
     * 날짜·시간별 [날짜, 시, 입장 수, 퇴장 수]. 입장은 입장 시각, 퇴장은 퇴장 시각의 시간에 센다. 간편입장은 순증감(`delta`)을 입장으로 센다.
     * 기록이 있는 시간만 나온다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT d, h, SUM(entered), SUM(exited) FROM (
                SELECT attendance_date AS d, CAST(EXTRACT(HOUR FROM entered_at) AS INTEGER) AS h, 1 AS entered, 0 AS exited
                FROM tb_entry_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                UNION ALL
                SELECT attendance_date, CAST(EXTRACT(HOUR FROM exited_at) AS INTEGER), 0, 1
                FROM tb_entry_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND exited_at IS NOT NULL
                UNION ALL
                SELECT attendance_date, CAST(EXTRACT(HOUR FROM created_at) AS INTEGER), delta, 0
                FROM tb_walk_in_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
            ) x GROUP BY d, h ORDER BY d, h
        """,
    )
    fun hourly(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<Array<Any?>>

    /**
     * 최신 입·퇴장 기록 [시각, 종류(ENTER·EXIT·CANCEL), 입장 종류, 구분, 입장 대상 ID, 스캐너]. 종이 QR의 입장 대상 ID는 토큰이라 응답에 싣지 않는다.
     * 간편입장은 입장 대상이 없고, 취소(`delta < 0`)는 `CANCEL`이다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT * FROM (
                SELECT entered_at AS occurred_at, 'ENTER' AS kind, subject_type, category, subject_id, scanner
                FROM tb_entry_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                UNION ALL
                SELECT exited_at, 'EXIT', subject_type, category, subject_id, exit_scanner
                FROM tb_entry_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND exited_at IS NOT NULL
                UNION ALL
                SELECT created_at, CASE WHEN delta > 0 THEN 'ENTER' ELSE 'CANCEL' END, 'WALK_IN', category, CAST(NULL AS VARCHAR), scanner
                FROM tb_walk_in_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
            ) r ORDER BY occurred_at DESC LIMIT :limit
        """,
    )
    fun recent(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<Array<Any?>>

    /** 스캐너별 [스캐너, 입장 수, 퇴장 수] 상위 [limit]개. 스캐너 이름을 보내지 않은 기록은 세지 않는다. 간편입장은 순증감을 입장으로 센다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT scanner, SUM(entered) AS entered, SUM(exited) AS exited FROM (
                SELECT scanner, 1 AS entered, 0 AS exited FROM tb_entry_log
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND scanner IS NOT NULL
                UNION ALL
                SELECT exit_scanner, 0, 1 FROM tb_entry_log
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND exit_scanner IS NOT NULL
                UNION ALL
                SELECT scanner, delta, 0 FROM tb_walk_in_log
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND scanner IS NOT NULL
            ) s GROUP BY scanner ORDER BY entered DESC, exited DESC, scanner LIMIT :limit
        """,
    )
    fun scanners(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<Array<Any?>>

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_log WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
