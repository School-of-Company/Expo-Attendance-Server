package team.startup.expo.domain.qr.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.qr.entity.QrToken

interface QrTokenRepository : JpaRepository<QrToken, String>
