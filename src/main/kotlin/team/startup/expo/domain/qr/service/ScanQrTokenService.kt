package team.startup.expo.domain.qr.service

import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.response.ScanQrTokenResDto

interface ScanQrTokenService {
    fun scan(
        expoId: String,
        reqDto: ScanQrTokenReqDto,
        scannerName: String? = null,
    ): ScanQrTokenResDto
}
