package team.startup.expo.domain.qr.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime

/**
 * 토큰 값이 PK라 id가 항상 채워져 있다. [Persistable]로 새 엔티티임을 알려야 `saveAll`이 토큰마다
 * SELECT를 먼저 날리지 않고 바로 INSERT한다.
 */
@Entity
@Table(name = "tb_qr_token", indexes = [Index(name = "ix_qr_token_expo", columnList = "expo_id")])
class QrToken(
    @field:Id
    @field:Column(length = 64)
    val token: String,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 30)
    val category: QrCategory,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** 박람회·구분별 연번. 연번 코드가 생기기 전에 발급한 토큰은 비어 있다. */
    @field:Column
    val serial: Int? = null,
    /** 사람이 읽는 연번 코드([QrTokenCode]). 박람회 안에서 유일하다. */
    @field:Column(length = 20)
    val code: String? = null,
    /** 분실 등으로 사용을 중지한 시각. 비어 있지 않으면 입장 스캔을 거부한다. */
    @field:Column(name = "revoked_at")
    val revokedAt: LocalDateTime? = null,
) : Persistable<String> {
    @field:jakarta.persistence.Transient
    private var newEntity: Boolean = true

    override fun getId(): String = token

    override fun isNew(): Boolean = newEntity

    @PostPersist
    @PostLoad
    fun markNotNew() {
        newEntity = false
    }
}
