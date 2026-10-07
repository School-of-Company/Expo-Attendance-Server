package team.startup.expo.domain.qr.entity

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
    name = "qr_entry",
    uniqueConstraints = [UniqueConstraint(name = "uk_qr_entry_token_date", columnNames = ["qr_token_id", "attendance_date"])],
)
class QrEntry(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "qr_token_id", nullable = false)
    val qrTokenId: Long,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate = LocalDate.now(),
    @field:Column(name = "entry_time", nullable = false)
    val entryTime: LocalDateTime = LocalDateTime.now(),
)
