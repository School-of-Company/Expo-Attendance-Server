package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    name = "tb_standard_program_attendance",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_standard_attendance_participant_program",
            columnNames = ["participant_id", "standard_program_id"],
        ),
    ],
    indexes = [Index(name = "ix_standard_attendance_program", columnList = "standard_program_id")],
)
class StandardProgramAttendance(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "standard_program_id", nullable = false)
    val standardProgramId: Long,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "entry_time", nullable = false)
    val entryTime: LocalTime,
    @field:Column(name = "leave_time")
    var leaveTime: LocalTime? = null,
)
