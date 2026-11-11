package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
     * SELECT로 끝나는 쓰기 CTE라 `@Modifying` 없이 호출하고, 쓰기 트랜잭션 안에서만 부른다.
     */
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
     * SELECT로 끝나는 쓰기 CTE라 `@Modifying` 없이 호출하고, 쓰기 트랜잭션 안에서만 부른다.
     */
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
     * SELECT로 끝나는 쓰기 CTE라 `@Modifying` 없이 호출하고, 쓰기 트랜잭션 안에서만 부른다.
     */
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

    /**
     * 종류·구분별 [종류, 구분, 입장 수, 지금 밖에 있는 수, 퇴장 스캔 수]. 구분은 종이 QR에만 있다.
     * 퇴장 스캔 수는 시간대별·스캐너별과 같은 `tb_entry_event`의 `EXIT` 횟수(#162)이고, 구분은 그 사람의 입장 로그에서 가져온다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT l.subject_type, l.category, COUNT(*), COUNT(l.exited_at), COALESCE(SUM(x.n), 0) FROM tb_entry_log l
            LEFT JOIN (
                SELECT subject_type, subject_id, attendance_date, COUNT(*) AS n FROM tb_entry_event
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND event_type = 'EXIT'
                GROUP BY subject_type, subject_id, attendance_date
            ) x ON x.subject_type = l.subject_type AND x.subject_id = l.subject_id AND x.attendance_date = l.attendance_date
            WHERE l.expo_id = :expoId AND l.attendance_date BETWEEN :from AND :to GROUP BY l.subject_type, l.category
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
     * 날짜·시간별 [날짜, 시, 입장 수, 퇴장 수]. 입장은 입장 시각, 퇴장은 퇴장 시각의 시간에 센다. 간편입장은 순증감(`delta`)을 입장으로 세고, 취소가 다른 시간에 들어와 음수가 되면 0으로 자른다.
     * 기록이 있는 시간만 나온다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT d, h, GREATEST(SUM(entered), 0), SUM(exited) FROM (
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
     *
     * 입장 날짜, 시각의 역순이다(기록은 그 입장 날짜에 일어나므로 시각 역순과 같다). 세 갈래가 각자 이 순서로 앞 [limit]개만 읽고 합친다(#255).
     * 입장 로그는 `ix_entry_log_expo_day_entered`, 퇴장은 입장 이벤트의 (박람회, 날짜, 시각) 인덱스를 거꾸로 읽어 그날 행을 모두 읽고 정렬하지 않는다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT occurred_at, kind, subject_type, category, subject_id, scanner FROM (
                (SELECT attendance_date, entered_at AS occurred_at, 'ENTER' AS kind, subject_type, category, subject_id, scanner
                 FROM tb_entry_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                 ORDER BY attendance_date DESC, entered_at DESC LIMIT :limit)
                UNION ALL
                (SELECT v.attendance_date, v.occurred_at, 'EXIT', v.subject_type, l.category, v.subject_id, v.scanner
                 FROM (
                     SELECT expo_id, subject_type, subject_id, attendance_date, occurred_at, scanner FROM tb_entry_event
                     WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to AND event_type = 'EXIT'
                     ORDER BY attendance_date DESC, occurred_at DESC LIMIT :limit
                 ) v JOIN tb_entry_log l
                   ON l.expo_id = v.expo_id AND l.subject_type = v.subject_type AND l.subject_id = v.subject_id AND l.attendance_date = v.attendance_date)
                UNION ALL
                (SELECT attendance_date, created_at, CASE WHEN delta > 0 THEN 'ENTER' ELSE 'CANCEL' END, 'WALK_IN', category, CAST(NULL AS VARCHAR), scanner
                 FROM tb_walk_in_log WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                 ORDER BY attendance_date DESC, created_at DESC LIMIT :limit)
            ) r ORDER BY attendance_date DESC, occurred_at DESC LIMIT :limit
        """,
    )
    fun recent(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<Array<Any?>>

    /** 스캐너별 [스캐너, 입장 수, 퇴장 수] 상위 [limit]개. 스캐너 이름을 보내지 않은 기록은 세지 않는다. 간편입장은 순증감을 입장으로 세고 음수는 0으로 자른다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT scanner, GREATEST(SUM(entered), 0) AS entered, SUM(exited) AS exited FROM (
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

    /** 그날 등록 QR(일반 참가자·연수자)로 입장한 사람 수(#237). 한 사람은 하루 한 행이다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT COUNT(*) FROM tb_entry_log
            WHERE expo_id = :expoId AND attendance_date = :date AND subject_type IN ('STANDARD', 'TRAINEE')
        """,
    )
    fun countRegisteredEntries(
        @Param("expoId") expoId: String,
        @Param("date") date: LocalDate,
    ): Long

    /**
     * 그날 등록 QR로 입장한 사람 한 페이지 [대상 종류, 대상 ID, 최초 입장 시각, 퇴장 시각](#237). 최초 입장 시각 최신순, 같으면 id 역순이다.
     * 재입장은 퇴장 시각만 비우므로 입장 시각은 그날 처음 입장한 시각이다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT subject_type, subject_id, entered_at, exited_at FROM tb_entry_log
            WHERE expo_id = :expoId AND attendance_date = :date AND subject_type IN ('STANDARD', 'TRAINEE')
            ORDER BY entered_at DESC, id DESC LIMIT :size OFFSET :offset
        """,
    )
    fun findRegisteredEntries(
        @Param("expoId") expoId: String,
        @Param("date") date: LocalDate,
        @Param("size") size: Int,
        @Param("offset") offset: Long,
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
     * 기간 안에서 (입장 날짜, 발생 시각, id)가 커서(`after*`)보다 뒤인 이벤트를 [offset]만큼 건너뛰고 [size]건 읽는다(#150, #250).
     * 입장 날짜, 발생 시각, id 순이다(이벤트는 그 입장 날짜에 일어나므로 발생 시각 순과 같다). 먼저 이벤트 표에서 `ix_entry_event_expo_day_id`
     * 순서대로 커서 위치부터 읽어 `LIMIT`/`OFFSET`을 한 뒤, 그 페이지 행에만 구분(`tb_entry_log`)과 코드(`tb_qr_token`)를 붙인다.
     * 입장 로그는 대상·날짜마다 한 행(유일 키)이고 토큰은 PK라 행이 늘어나지 않는다. 커서로 이어 읽으면(`offset` 0) 몇 번째 페이지든 비용이 같고,
     * 처음부터 읽을 때는 [EntryRecordCursor.before]로 기간 앞을 커서로 준다. 같은 순서라 OFFSET 페이지의 마지막 행에서 이어 읽어도 겹침과 누락이 없다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT e.occurred_at AS "occurredAt", e.event_type AS "eventType", e.subject_type AS "subjectType",
                   e.subject_id AS "subjectId", l.category AS "category", q.code AS "paperCode", e.scanner AS "scanner",
                   e.id AS "id", e.attendance_date AS "attendanceDate"
            FROM (
                SELECT id, expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner
                FROM tb_entry_event
                WHERE expo_id = :expoId AND attendance_date BETWEEN :from AND :to
                  AND (attendance_date, occurred_at, id) > (:afterDate, :afterAt, :afterId)
                ORDER BY attendance_date, occurred_at, id LIMIT :size OFFSET :offset
            ) e
            LEFT JOIN tb_entry_log l
              ON l.expo_id = e.expo_id AND l.subject_type = e.subject_type AND l.subject_id = e.subject_id AND l.attendance_date = e.attendance_date
            LEFT JOIN tb_qr_token q
              ON e.subject_type = 'PAPER_QR' AND q.expo_id = e.expo_id AND q.token = e.subject_id
            ORDER BY e.attendance_date, e.occurred_at, e.id
        """,
    )
    fun findEvents(
        @Param("expoId") expoId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("afterDate") afterDate: LocalDate,
        @Param("afterAt") afterAt: LocalDateTime,
        @Param("afterId") afterId: Long,
        @Param("size") size: Int,
        @Param("offset") offset: Long,
    ): List<EntryEventRow>

    /**
     * 이 트랜잭션의 쿼리를 매번 실제 값으로 계획하게 한다(#250). 같은 SQL이 서버 쪽 준비문으로 바뀌면 PostgreSQL이 값과 무관한 일반 계획을 고를 수 있는데,
     * 키셋 조회는 그 계획에서 인덱스 순서 읽기 대신 범위 전체를 읽어 정렬한다(측정: 페이지당 0.2ms → 40ms).
     */
    @Query(nativeQuery = true, value = "SELECT set_config('plan_cache_mode', 'force_custom_plan', true)")
    fun planWithActualValues(): String

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_event WHERE expo_id = :expoId")
    fun deleteEventsByExpoId(
        @Param("expoId") expoId: String,
    ): Int

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_log WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
