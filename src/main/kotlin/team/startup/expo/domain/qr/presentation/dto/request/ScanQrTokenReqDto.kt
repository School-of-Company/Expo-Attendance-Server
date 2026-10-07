package team.startup.expo.domain.qr.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ScanQrTokenReqDto(
    @field:NotBlank
    @field:Size(max = 64)
    val token: String,
) {
    // 토큰은 "이 종이를 받았다"는 증명이라 로그에 남지 않게 한다
    override fun toString() = "ScanQrTokenReqDto(token=***)"
}
