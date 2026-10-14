package team.startup.expo.domain.qr.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import team.startup.expo.domain.qr.entity.QrCategory

@Schema(description = "종이 QR 다시 조회 요청")
data class GetQrTokensReqDto(
    @field:Schema(description = "구분", example = "ADULT", requiredMode = Schema.RequiredMode.REQUIRED)
    val category: QrCategory,
    @field:Schema(description = "시작 연번(이 연번부터). 없으면 1", example = "1", minimum = "1", defaultValue = "1")
    @field:Min(1)
    val fromSerial: Int = 1,
    @field:Schema(description = "조회 개수(1~120). 없으면 120", example = "120", minimum = "1", maximum = "120", defaultValue = "120")
    @field:Min(1)
    @field:Max(MAX_COUNT.toLong())
    val count: Int = MAX_COUNT,
) {
    companion object {
        const val MAX_COUNT = 120
    }
}
