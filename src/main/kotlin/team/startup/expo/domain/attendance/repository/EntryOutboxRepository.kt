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

    /**
     * 발행하지 않은 이벤트를 오래된 순서로 잠그고 읽는다. `SKIP LOCKED`라 인스턴스가 여럿이어도 같은 행을
     * 동시에 발행하지 않는다. 트랜잭션 안에서 불러야 잠금이 유지된다.
     */
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM tb_entry_outbox WHERE status = 'PENDING' ORDER BY created_at, id LIMIT :limit FOR UPDATE SKIP LOCKED",
    )
    fun findPendingForUpdate(
        @Param("limit") limit: Int,
    ): List<EntryOutbox>

    /** 발행을 마치고 보관 기간이 지난 이벤트를 지운다. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_outbox WHERE status = 'PUBLISHED' AND published_at < :cutoff")
    fun deletePublishedBefore(
        @Param("cutoff") cutoff: LocalDateTime,
    ): Int

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

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_outbox WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
