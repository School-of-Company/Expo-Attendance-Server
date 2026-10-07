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
    name = "tb_training_program_attendance",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_training_attendance_trainee_program",
            columnNames = ["trainee_id", "training_program_id"],
        ),
    ],
    indexes = [Index(name = "ix_training_attendance_program", columnList = "training_program_id")],
)
class TrainingProgramAttendance(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "training_program_id", nullable = false)
    val trainingProgramId: Long,
    @field:Column(name = "trainee_id", nullable = false)
    val traineeId: Long,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "entry_time", nullable = false)
    val entryTime: LocalTime,
    @field:Column(name = "leave_time")
    var leaveTime: LocalTime? = null,
)
