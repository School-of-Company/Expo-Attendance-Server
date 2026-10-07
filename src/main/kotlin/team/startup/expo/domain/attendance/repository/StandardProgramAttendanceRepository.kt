package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.attendance.entity.StandardProgramAttendance

interface StandardProgramAttendanceRepository : JpaRepository<StandardProgramAttendance, Long> {
    fun findByParticipantIdAndStandardProgramId(
        participantId: Long,
        standardProgramId: Long,
    ): StandardProgramAttendance?
}
