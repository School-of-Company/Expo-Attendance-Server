package team.startup.expo.domain.qr.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tb_qr_token", indexes = [Index(name = "ix_qr_token_expo", columnList = "expo_id")])
class QrToken(
    @field:Id
    @field:Column(length = 64)
    val token: String,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    // Form-Server가 정의한 구분(enum) 값을 그대로 저장한다. 값 목록은 여기서 정하지 않는다.
    @field:Column(nullable = false, length = 30)
    val category: String,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
