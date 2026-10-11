package team.startup.expo.domain.qr.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import team.startup.expo.domain.qr.entity.QrCategory

@Schema(description = "종이 QR 토큰 발급 요청")
data class IssueQrTokensReqDto(
    @field:Schema(description = "발급할 토큰 수(1~1000)", example = "100", minimum = "1", maximum = "1000")
    @field:Min(1)
    @field:Max(MAX_COUNT.toLong())
    val count: Int,
    @field:Schema(description = "종이를 받는 사람의 구분. 현장에서는 `ADULT`, `CHILD`(바쁘면 `GENERAL`)를 주로 쓴다", example = "ADULT")
    val category: QrCategory,
) {
    companion object {
        const val MAX_COUNT = 1000
    }
}
