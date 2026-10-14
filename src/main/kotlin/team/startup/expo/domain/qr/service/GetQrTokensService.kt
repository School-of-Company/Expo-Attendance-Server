package team.startup.expo.domain.qr.service

import team.startup.expo.domain.qr.presentation.dto.request.GetQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenListResDto

interface GetQrTokensService {
    fun get(
        expoId: String,
        reqDto: GetQrTokensReqDto,
    ): QrTokenListResDto
}
