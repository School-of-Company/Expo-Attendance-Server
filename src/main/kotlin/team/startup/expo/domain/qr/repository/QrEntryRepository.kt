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
     * 반환값이 0이면 이미 입장했거나 없는 토큰이다.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_qr_entry (token, attendance_date, entered_at, period_check)
            SELECT t.token, :attendanceDate, :enteredAt, :periodCheck FROM tb_qr_token t
            WHERE t.token = :token AND t.expo_id = :expoId
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
