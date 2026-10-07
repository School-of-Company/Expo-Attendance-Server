package team.startup.expo.domain.qr.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrEntry
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
            INSERT INTO tb_qr_entry (token, attendance_date, entered_at)
            SELECT t.token, :attendanceDate, :enteredAt FROM tb_qr_token t
            WHERE t.token = :token AND t.expo_id = :expoId
            ON CONFLICT (token, attendance_date) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("token") token: String,
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("enteredAt") enteredAt: LocalDateTime,
    ): Int

    fun existsByToken(token: String): Boolean
}
