package team.startup.expo.domain.qr.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrEntry
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import java.time.LocalDate
import java.time.LocalDateTime

interface QrEntryRepository : JpaRepository<QrEntry, Long> {
    /**
     * 토큰이 해당 박람회 것이고 그 날짜에 아직 입장하지 않았을 때만 입장을 기록한다.
     * 무효화한 토큰은 기록하지 않는다. 반환값이 0이면 이미 입장했거나 없는 토큰이거나 무효화한 토큰이다.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_qr_entry (token, attendance_date, entered_at, period_check)
            SELECT t.token, :attendanceDate, :enteredAt, :periodCheck FROM tb_qr_token t
            WHERE t.token = :token AND t.expo_id = :expoId AND t.revoked_at IS NULL
            ON CONFLICT (token, attendance_date) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("token") token: String,
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("enteredAt") enteredAt: LocalDateTime,
        @Param("periodCheck") periodCheck: String,
    ): Int

    /**
     * [insertIfAbsent]와 같은 규칙으로 입장을 기록하면서, 같은 문장 안에서 입장 로그(`tb_entry_log`, 토큰 구분 포함)와 입장 이력(`tb_entry_event`)도 남긴다.
     * 한 문장이라 둘이 함께 커밋되거나 함께 빠지므로, 입장은 됐는데 로그만 없어 퇴장 스캔이 막히는 일이 없다.
     * 반환값은 새로 기록한 입장 수다(1이면 입장, 0이면 이미 입장했거나 없는 토큰이거나 무효화한 토큰).
     */
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            WITH entered AS (
                INSERT INTO tb_qr_entry (token, attendance_date, entered_at, period_check)
                SELECT t.token, :attendanceDate, :enteredAt, :periodCheck FROM tb_qr_token t
                WHERE t.token = :token AND t.expo_id = :expoId AND t.revoked_at IS NULL
                ON CONFLICT (token, attendance_date) DO NOTHING
                RETURNING token
            ), logged AS (
                INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, category, scanner)
                SELECT t.expo_id, 'PAPER_QR', t.token, :attendanceDate, :enteredAt, t.category, :scanner
                FROM tb_qr_token t JOIN entered e ON e.token = t.token
                ON CONFLICT (expo_id, subject_type, subject_id, attendance_date) DO NOTHING
                RETURNING expo_id, subject_id
            ), recorded AS (
                INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
                SELECT expo_id, 'PAPER_QR', subject_id, :attendanceDate, 'ENTER', :enteredAt, :scanner FROM logged
                RETURNING 1
            )
            SELECT COUNT(*) FROM entered
        """,
    )
    fun recordEntry(
        @Param("token") token: String,
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("enteredAt") enteredAt: LocalDateTime,
        @Param("periodCheck") periodCheck: String,
        @Param("scanner") scanner: String?,
    ): Int

    /**
     * 오늘 퇴장한 종이 QR이 다시 입장하면 재입장으로 기록한다(#148). 이 박람회의 무효화하지 않은 토큰만 해당하고, 퇴장 시각을 비워
     * 다시 "안에 있는" 상태로 되돌리면서 재입장 이력을 남긴다. 반환값이 0이면 퇴장한 상태가 아니다(퇴장 없이 다시 찍은 것은 재입장이 아니다).
     */
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            WITH reopened AS (
                UPDATE tb_entry_log l SET exited_at = NULL, exit_scanner = NULL
                WHERE l.expo_id = :expoId AND l.subject_type = 'PAPER_QR' AND l.subject_id = :token
                  AND l.attendance_date = :attendanceDate AND l.exited_at IS NOT NULL
                  AND EXISTS (SELECT 1 FROM tb_qr_token t WHERE t.token = :token AND t.expo_id = :expoId AND t.revoked_at IS NULL)
                RETURNING l.subject_id
            ), recorded AS (
                INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
                SELECT :expoId, 'PAPER_QR', subject_id, :attendanceDate, 'RE_ENTER', :occurredAt, :scanner FROM reopened
                RETURNING 1
            )
            SELECT COUNT(*) FROM reopened
        """,
    )
    fun reenter(
        @Param("token") token: String,
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("occurredAt") occurredAt: LocalDateTime,
        @Param("scanner") scanner: String?,
    ): Int

    /** 박람회 서비스 장애 중 기간 확인 없이 기록되어 아직 다시 확인하지 못한 입장 수. 부분 인덱스(`period_check = 'PENDING'`)를 탄다. */
    @Query(nativeQuery = true, value = "SELECT count(*) FROM tb_qr_entry WHERE period_check = 'PENDING'")
    fun countPending(): Long

    /** 기간 확인을 기다리는 입장이 있는 박람회 ID. 박람회 ID는 토큰에서 가져온다. */
    @Query("SELECT DISTINCT t.expoId FROM QrEntry e JOIN QrToken t ON t.token = e.token WHERE e.periodCheck = :status")
    fun findExpoIdsByPeriodCheck(
        @Param("status") status: QrEntryPeriodCheck,
    ): List<String>

    /** 한 박람회에서 기간 확인을 기다리는 입장을 오래된 순서로 읽는다. */
    @Query(
        "SELECT new team.startup.expo.domain.qr.repository.PendingQrEntry(e.id, t.expoId, e.attendanceDate) " +
            "FROM QrEntry e JOIN QrToken t ON t.token = e.token " +
            "WHERE e.periodCheck = :status AND t.expoId = :expoId ORDER BY e.id",
    )
    fun findByPeriodCheckAndExpoId(
        @Param("status") status: QrEntryPeriodCheck,
        @Param("expoId") expoId: String,
        pageable: Pageable,
    ): List<PendingQrEntry>

    @Transactional
    @Modifying
    @Query("UPDATE QrEntry e SET e.periodCheck = :status WHERE e.id IN :ids")
    fun updatePeriodCheck(
        @Param("ids") ids: Collection<Long>,
        @Param("status") status: QrEntryPeriodCheck,
    ): Int

    fun existsByToken(token: String): Boolean
}

/** 기간 확인을 기다리는 입장 한 건. */
data class PendingQrEntry(
    val id: Long,
    val expoId: String,
    val attendanceDate: LocalDate,
)
