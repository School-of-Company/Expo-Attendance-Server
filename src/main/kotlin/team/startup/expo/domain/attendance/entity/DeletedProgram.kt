package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tb_deleted_program")
class DeletedProgram(
    @field:Id
    @field:Column(name = "program_key", length = 40)
    val programKey: String,
    @field:Column(name = "deleted_at", nullable = false)
    val deletedAt: LocalDateTime,
)
