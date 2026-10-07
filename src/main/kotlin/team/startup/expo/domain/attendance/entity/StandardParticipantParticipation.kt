package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_standard_participant_participation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_standard_participant_participation",
            columnNames = ["expo_id", "standard_participant_id", "attendance_date"],
        ),
    ],
)
class StandardParticipantParticipation(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "entry_time", nullable = false)
    val entryTime: LocalDateTime,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "standard_participant_id", nullable = false)
    val standardParticipantId: Long,
    @field:Column(name = "expo_id", nullable = false, length = 64)
    val expoId: String,
)
