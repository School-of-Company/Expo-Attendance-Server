package team.startup.expo.domain.qr.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "종이 QR 입구 스캔 요청")
data class ScanQrTokenReqDto(
    @field:Schema(description = "종이 QR에 담긴 토큰(최대 64자). \"이 종이를 받았다\"는 증명이라 로그에 남기지 않는다", example = "sample-token-not-real", minLength = 1)
    @field:NotBlank
    @field:Size(max = 64)
    val token: String,
) {
    // 토큰은 "이 종이를 받았다"는 증명이라 로그에 남지 않게 한다
    override fun toString() = "ScanQrTokenReqDto(token=***)"
}
