package team.startup.expo.domain.qr.service

import team.startup.expo.domain.qr.presentation.dto.response.QrTokenSummaryResDto

interface GetQrTokenSummaryService {
    fun get(expoId: String): QrTokenSummaryResDto
}
