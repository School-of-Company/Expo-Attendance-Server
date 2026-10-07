package team.startup.expo.domain.qr.presentation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class IssueQrTokensReqDto(
    @field:Min(1)
    @field:Max(MAX_COUNT.toLong())
    val count: Int,
    @field:NotBlank
    @field:Size(max = 30)
    val category: String = DEFAULT_CATEGORY,
) {
    companion object {
        const val MAX_COUNT = 1000
        const val DEFAULT_CATEGORY = "STANDARD"
    }
}
