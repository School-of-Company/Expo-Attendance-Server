package team.startup.expo.domain.qr.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrToken
import java.time.LocalDateTime

interface QrTokenRepository : JpaRepository<QrToken, String> {
    fun existsByTokenAndExpoId(
        token: String,
        expoId: String,
    ): Boolean

    /** 이 박람회·구분에서 이미 매긴 가장 큰 연번(없으면 0). 발급은 박람회 락 안에서 하므로 겹치지 않는다. */
    @Query("SELECT COALESCE(MAX(t.serial), 0) FROM QrToken t WHERE t.expoId = :expoId AND t.category = :category")
    fun findMaxSerial(
        @Param("expoId") expoId: String,
        @Param("category") category: QrCategory,
    ): Int

    fun existsByTokenAndExpoIdAndRevokedAtIsNotNull(
        token: String,
        expoId: String,
    ): Boolean

    /** 연번이 있는 토큰을 연번 순으로 읽는다. */
    @Query("SELECT t FROM QrToken t WHERE t.expoId = :expoId AND t.category = :category AND t.serial >= :fromSerial ORDER BY t.serial")
    fun findByRange(
        @Param("expoId") expoId: String,
        @Param("category") category: QrCategory,
        @Param("fromSerial") fromSerial: Int,
        pageable: Pageable,
    ): List<QrToken>

    /** 구분별 사용 중·무효화 수. 연번이 없는 이전 토큰도 센다. */
    @Query(
        "SELECT new team.startup.expo.domain.qr.repository.QrTokenCategoryCount(" +
            "t.category, SUM(CASE WHEN t.revokedAt IS NULL THEN 1L ELSE 0L END), SUM(CASE WHEN t.revokedAt IS NULL THEN 0L ELSE 1L END)) " +
            "FROM QrToken t WHERE t.expoId = :expoId GROUP BY t.category",
    )
    fun countByCategory(
        @Param("expoId") expoId: String,
    ): List<QrTokenCategoryCount>

    /** 현장 종이 QR 코드(#243)를 첫 스캔 때 등록한다. 이미 있으면 아무것도 하지 않는다. 연번은 발급 방식과 겹치지 않게 비워 둔다. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_qr_token (token, expo_id, category, created_at, code)
            VALUES (:token, :expoId, :category, :now, :code)
            ON CONFLICT DO NOTHING
        """,
    )
    fun registerField(
        @Param("token") token: String,
        @Param("expoId") expoId: String,
        @Param("category") category: String,
        @Param("code") code: String,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 이미 무효화한 것은 시각을 그대로 두고, 코드가 없으면 0을 돌려준다. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = "UPDATE tb_qr_token SET revoked_at = COALESCE(revoked_at, :now) WHERE expo_id = :expoId AND code = :code",
    )
    fun revokeByCode(
        @Param("expoId") expoId: String,
        @Param("code") code: String,
        @Param("now") now: LocalDateTime,
    ): Int

    /** 입장 기록은 외래키의 `ON DELETE CASCADE`로 함께 지워진다. */
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_qr_token WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}

/** 구분별 종이 QR 수. */
data class QrTokenCategoryCount(
    val category: QrCategory,
    val active: Long,
    val revoked: Long,
)
