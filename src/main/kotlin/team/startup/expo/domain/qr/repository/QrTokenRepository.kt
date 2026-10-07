package team.startup.expo.domain.qr.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrToken

interface QrTokenRepository : JpaRepository<QrToken, String> {
    fun existsByTokenAndExpoId(
        token: String,
        expoId: String,
    ): Boolean

    /** 입장 기록이 한 번이라도 있는 토큰의 박람회 id. 없는 토큰과 입장 전 토큰은 둘 다 `null`이다. */
    @Query("SELECT t.expoId FROM QrToken t WHERE t.token = :token AND EXISTS (SELECT 1 FROM QrEntry e WHERE e.token = t.token)")
    fun findEnteredExpoId(
        @Param("token") token: String,
    ): String?

    /** 입장 기록은 외래키의 `ON DELETE CASCADE`로 함께 지워진다. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_qr_token WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
