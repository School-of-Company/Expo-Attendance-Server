package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_standard_program_user")
class StandardProgramUser(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(nullable = false)
    var status: Boolean = false,
    @field:Column(name = "entry_time", length = 20)
    var entryTime: String? = null,
    @field:Column(name = "leave_time", length = 20)
    var leaveTime: String? = null,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "standard_program_id", nullable = false)
    val standardProgramId: Long,
    @field:Column(name = "standard_participant_id", nullable = false)
    val standardParticipantId: Long,
) {
    fun addEntryTime(entryTime: String) {
        this.status = true
        this.entryTime = entryTime
    }

    fun addLeaveTime(leaveTime: String) {
        this.leaveTime = leaveTime
    }
}
