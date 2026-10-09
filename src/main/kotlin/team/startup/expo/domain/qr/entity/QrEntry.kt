package team.startup.expo.domain.qr.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_qr_entry",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_qr_entry_token_date", columnNames = ["token", "attendance_date"]),
    ],
)
class QrEntry(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(nullable = false, length = 64)
    val token: String,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "entered_at", nullable = false)
    val enteredAt: LocalDateTime,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "period_check", nullable = false, length = 20)
    val periodCheck: QrEntryPeriodCheck = QrEntryPeriodCheck.VERIFIED,
)
