package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "tb_entry_outbox",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_entry_outbox_phone_date",
            columnNames = ["expo_id", "phone_number", "attendance_date"],
        ),
    ],
    indexes = [Index(name = "ix_entry_outbox_status_created", columnList = "status, created_at")],
)
class EntryOutbox(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "event_id", nullable = false, unique = true)
    val eventId: UUID = UUID.randomUUID(),
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    @field:Column(name = "phone_number", nullable = false, length = 15)
    val phoneNumber: String,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 10)
    var status: OutboxStatus = OutboxStatus.PENDING,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @field:Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,
) {
    fun markPublished(publishedAt: LocalDateTime) {
        this.status = OutboxStatus.PUBLISHED
        this.publishedAt = publishedAt
    }

    /** `publishedAt`은 "처리를 마친 시각"이라 포기한 이벤트도 여기에 남겨 보관 기간 계산에 쓴다. */
    fun markFailed(failedAt: LocalDateTime) {
        this.status = OutboxStatus.FAILED
        this.publishedAt = failedAt
    }
}
