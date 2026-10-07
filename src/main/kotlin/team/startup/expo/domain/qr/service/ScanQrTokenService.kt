package team.startup.expo.domain.qr.service

import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto

interface ScanQrTokenService {
    fun scan(
        expoId: String,
        reqDto: ScanQrTokenReqDto,
    )
}
