package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "종이 QR 다시 조회 결과")
data class QrTokenListResDto(
    @field:Schema(description = "연번 순서. 범위에 발급분이 없으면 비어 있다", requiredMode = Schema.RequiredMode.REQUIRED)
    val items: List<QrTokenItemResDto>,
) {
    override fun toString() = "QrTokenListResDto(size=${items.size})"
}
