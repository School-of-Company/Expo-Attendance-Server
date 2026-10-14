package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.qr.entity.QrCategory

@Schema(description = "종이 QR 발급 현황")
data class QrTokenSummaryResDto(
    @field:Schema(description = "발급한 적 있는 구분별 수량(구분 이름 순)", requiredMode = Schema.RequiredMode.REQUIRED)
    val categories: List<QrTokenCategorySummaryResDto>,
)

@Schema(description = "한 구분의 종이 QR 수량")
data class QrTokenCategorySummaryResDto(
    @field:Schema(description = "구분", example = "ADULT", requiredMode = Schema.RequiredMode.REQUIRED)
    val category: QrCategory,
    @field:Schema(description = "사용 중인 수", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    val active: Long,
    @field:Schema(description = "무효화한 수", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    val revoked: Long,
)
