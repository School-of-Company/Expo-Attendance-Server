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

    /**
     * 오늘 입장 로그를 남기고, 같은 문장 안에서 입장 이력(`tb_entry_event`)도 남긴다. 이미 있으면 아무것도 하지 않는다(같은 날 최초
     * 입장은 하루 한 행). 반환값은 새로 남긴 로그 수다. 재입장은 [reenter]가 맡는다.
     */
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            WITH entered AS (
                INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, category, scanner)
                VALUES (:expoId, :subjectType, :subjectId, :attendanceDate, :enteredAt, :category, :scanner)
                ON CONFLICT (expo_id, subject_type, subject_id, attendance_date) DO NOTHING
                RETURNING subject_id
            ), logged AS (
                INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
                SELECT :expoId, :subjectType, :subjectId, :attendanceDate, 'ENTER', :enteredAt, :scanner FROM entered
                RETURNING 1
            )
            SELECT COUNT(*) FROM entered
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

    /**
     * 오늘 퇴장한 사람이 다시 입장하면 재입장으로 기록한다(#148). 퇴장 시각을 비워 다시 "안에 있는" 상태로 되돌리고, 재입장 이력을 남긴다.
     * 반환값이 0이면 오늘 입장한 적이 없거나 퇴장하지 않은 상태다(퇴장 없이 다시 찍은 것은 재입장이 아니다).
     */
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            WITH reopened AS (
                UPDATE tb_entry_log SET exited_at = NULL, exit_scanner = NULL
                WHERE expo_id = :expoId AND subject_type = :subjectType AND subject_id = :subjectId
                  AND attendance_date = :attendanceDate AND exited_at IS NOT NULL
                RETURNING subject_id
            ), logged AS (
                INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
                SELECT :expoId, :subjectType, :subjectId, :attendanceDate, 'RE_ENTER', :occurredAt, :scanner FROM reopened
                RETURNING 1
            )
            SELECT COUNT(*) FROM reopened
        """,
    )
    fun reenter(
        @Param("expoId") expoId: String,
        @Param("subjectType") subjectType: String,
        @Param("subjectId") subjectId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("occurredAt") occurredAt: LocalDateTime,
        @Param("scanner") scanner: String?,
    ): Int

    /**
     * 오늘 입장했고 지금 퇴장하지 않은 경우에만 퇴장 시각을 기록하고, 같은 문장 안에서 퇴장 이력도 남긴다. 0이면 입장 기록이 없거나
     * 이미 퇴장한 상태다. 재입장한 사람은 다시 퇴장할 수 있다.
     */
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            WITH exited AS (
                UPDATE tb_entry_log SET exited_at = :exitedAt, exit_scanner = :exitScanner
                WHERE expo_id = :expoId AND subject_type = :subjectType AND subject_id = :subjectId
                  AND attendance_date = :attendanceDate AND exited_at IS NULL
                RETURNING subject_id
            ), logged AS (
                INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
                SELECT :expoId, :subjectType, :subjectId, :attendanceDate, 'EXIT', :exitedAt, :exitScanner FROM exited
                RETURNING 1
            )
            SELECT COUNT(*) FROM exited
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

    /** 기간 안의 재입장 건수(`tb_entry_event`의 `RE_ENTER`). 같은 사람이 여러 번 재입장하면 그만큼 센다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT COUNT(*) FROM tb_entry_event
            WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND event_type = 'RE_ENTER'
        """,
    )
    fun countReentered(
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
                SELECT attendance_date, CAST(EXTRACT(HOUR FROM occurred_at) AS INTEGER), 0, 1
                FROM tb_entry_event WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND event_type = 'EXIT'
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
                SELECT v.occurred_at, 'EXIT', v.subject_type, l.category, v.subject_id, v.scanner
                FROM tb_entry_event v JOIN tb_entry_log l
                  ON l.expo_id = v.expo_id AND l.subject_type = v.subject_type AND l.subject_id = v.subject_id AND l.attendance_date = v.attendance_date
                WHERE v.expo_id = :expoId AND v.attendance_date BETWEEN :from AND :to AND v.event_type = 'EXIT'
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
                SELECT scanner, 0, 1 FROM tb_entry_event
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND event_type = 'EXIT' AND scanner IS NOT NULL
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

    /** 기간 안의 입장·재입장·퇴장 이벤트 수(#150). */
    @Query(
        nativeQuery = true,
        value = "SELECT COUNT(*) FROM tb_entry_event WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to",
    )
    fun countEvents(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): Long

    /**
     * 기간 안의 이벤트 한 페이지 [시각, 종류, 대상 종류, 대상 ID, 종이 QR 구분, 종이 QR 코드, 스캐너](#150). 발생 시각, 같으면 id 순이다.
     * 먼저 이벤트 표에서 `ix_entry_event_expo_day`로 범위를 거르고 정렬·`LIMIT`/`OFFSET`을 한 뒤, 그 페이지 행에만 구분(`tb_entry_log`)과
     * 코드(`tb_qr_token`)를 붙인다. 입장 로그는 대상·날짜마다 한 행(유일 키)이고 토큰은 PK라 행이 늘어나지 않는다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT e.occurred_at, e.event_type, e.subject_type, e.subject_id, l.category, q.code, e.scanner
            FROM (
                SELECT id, expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner
                FROM tb_entry_event
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                ORDER BY occurred_at, id LIMIT :size OFFSET :offset
            ) e
            LEFT JOIN tb_entry_log l
              ON l.expo_id = e.expo_id AND l.subject_type = e.subject_type AND l.subject_id = e.subject_id AND l.attendance_date = e.attendance_date
            LEFT JOIN tb_qr_token q
              ON e.subject_type = 'PAPER_QR' AND q.expo_id = e.expo_id AND q.token = e.subject_id
            ORDER BY e.occurred_at, e.id
        """,
    )
    fun findEvents(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("size") size: Int,
        @Param("offset") offset: Long,
    ): List<Array<Any?>>

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_event WHERE expo_id = :expoId")
    fun deleteEventsByExpoId(
        @Param("expoId") expoId: String,
    ): Int

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_log WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
