package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.attendance.entity.EntryOutbox
import java.time.LocalDate

interface EntryOutboxRepository : JpaRepository<EntryOutbox, Long> {
    fun existsByExpoIdAndParticipantIdAndAttendanceDate(
        expoId: String,
        participantId: Long,
        attendanceDate: LocalDate,
    ): Boolean
}
