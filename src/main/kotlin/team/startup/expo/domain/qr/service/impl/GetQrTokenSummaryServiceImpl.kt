package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenCategorySummaryResDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenSummaryResDto
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.GetQrTokenSummaryService

@Service
class GetQrTokenSummaryServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
) : GetQrTokenSummaryService {
    @Transactional(readOnly = true)
    override fun get(expoId: String): QrTokenSummaryResDto =
        QrTokenSummaryResDto(
            qrTokenRepository
                .countByCategory(expoId)
                .sortedBy { it.category.name }
                .map { QrTokenCategorySummaryResDto(it.category, it.active, it.revoked) },
        )
}
