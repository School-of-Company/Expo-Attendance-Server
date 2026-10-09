package team.startup.expo.domain.qr.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tb_deleted_expo")
class DeletedExpo(
    @field:Id
    @field:Column(name = "expo_id", length = 36)
    val expoId: String,
    @field:Column(name = "deleted_at", nullable = false)
    val deletedAt: LocalDateTime,
)
