package team.startup.expo.domain.attendance.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class FieldEntryFence(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun begin(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO tb_entry_scan_fence (attempt_id, expo_id, participant_id, attendance_date) VALUES (?, ?, ?, ?)",
            id,
            expoId,
            participantId,
            date,
        )
        return id
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finish(
        attemptId: UUID,
        entered: Boolean,
    ) {
        if (entered) {
            jdbc.update("UPDATE tb_entry_scan_fence SET entered = TRUE WHERE attempt_id = ?", attemptId)
        } else {
            jdbc.update("DELETE FROM tb_entry_scan_fence WHERE attempt_id = ?", attemptId)
        }
    }

    fun entered(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): Boolean = exists(expoId, participantId, date, true)

    fun pending(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): Boolean = exists(expoId, participantId, date, false)

    private fun exists(
        expoId: String,
        participantId: Long,
        date: LocalDate,
        entered: Boolean,
    ): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tb_entry_scan_fence WHERE expo_id = ? AND participant_id = ? AND attendance_date = ? AND entered = ?)",
            Boolean::class.java,
            expoId,
            participantId,
            date,
            entered,
        ) == true
}
