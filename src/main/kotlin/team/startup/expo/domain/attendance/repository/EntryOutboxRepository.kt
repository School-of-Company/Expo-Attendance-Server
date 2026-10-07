package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntryOutbox
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface EntryOutboxRepository : JpaRepository<EntryOutbox, Long> {
    fun existsByExpoIdAndParticipantIdAndAttendanceDate(
        expoId: String,
        participantId: Long,
        attendanceDate: LocalDate,
    ): Boolean

    /** 같은 날 같은 참가자의 행이 있으면 아무것도 하지 않는다. 반환값 1이면 새로 남긴 것이다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_entry_outbox (event_id, expo_id, participant_id, phone_number, attendance_date, status, created_at)
            VALUES (:eventId, :expoId, :participantId, :phoneNumber, :attendanceDate, 'PENDING', :createdAt)
            ON CONFLICT (expo_id, participant_id, attendance_date) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("eventId") eventId: UUID,
        @Param("expoId") expoId: String,
        @Param("participantId") participantId: Long,
        @Param("phoneNumber") phoneNumber: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Int
}
