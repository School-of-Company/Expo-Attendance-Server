package team.startup.expo.domain.qr.presentation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import team.startup.expo.domain.qr.entity.QrCategory

data class IssueQrTokensReqDto(
    @field:Min(1)
    @field:Max(MAX_COUNT.toLong())
    val count: Int,
    val category: QrCategory,
) {
    companion object {
        const val MAX_COUNT = 1000
    }
}
