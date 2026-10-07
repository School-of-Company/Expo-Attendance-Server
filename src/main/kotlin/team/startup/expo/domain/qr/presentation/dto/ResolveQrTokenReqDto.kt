package team.startup.expo.domain.qr.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** `toString`을 덮어 토큰이 로그에 남지 않게 한다. */
data class ResolveQrTokenReqDto(
    @field:NotBlank
    @field:Size(max = 64)
    val token: String,
) {
    override fun toString() = "ResolveQrTokenReqDto(token=***)"
}
