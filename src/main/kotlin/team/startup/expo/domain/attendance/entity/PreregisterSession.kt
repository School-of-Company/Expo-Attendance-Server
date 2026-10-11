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

/** 사전등록 참가자 한 명이 박람회에서 신청한 회차(박람회당 하나). 참가자와 회차는 ID로만 가리킨다. */
@Entity
@Table(
    name = "tb_preregister_session",
    uniqueConstraints = [UniqueConstraint(name = "uk_preregister_session_participant", columnNames = ["expo_id", "participant_id"])],
)
class PreregisterSession(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    /** 확정 기록 없이 취소만 먼저 도착한 참가자는 회차가 없다. */
    @field:Column(name = "session_id")
    val sessionId: Long?,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 20)
    val status: PreregisterSessionStatus,
    /** 신청 서비스가 보낸 가장 새로운 revision. revision 없이 보낸 요청만 있었다면 0이다. */
    @field:Column(name = "revision", nullable = false)
    val revision: Long = 0,
    @field:Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,
)
