package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/** 신청 취소가 진행 중이거나 확정된 참가자(박람회당 하나). 참가자와 연수자는 ID로만 가리킨다. */
@Entity
@Table(
    name = "tb_cancellation_guard",
    uniqueConstraints = [UniqueConstraint(name = "uk_cancellation_guard_participant", columnNames = ["expo_id", "participant_id"])],
)
class CancellationGuard(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    /** 이 참가자에 연결된 연수자. 연수 프로그램 스캔을 막으려고 취소 검사 중에 채운다. */
    @field:Column(name = "trainee_id")
    val traineeId: Long? = null,
    @field:Column(name = "operation_id", nullable = false, length = 64)
    val operationId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "state", nullable = false, length = 20)
    val state: CancellationGuardState,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    @field:Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,
)
