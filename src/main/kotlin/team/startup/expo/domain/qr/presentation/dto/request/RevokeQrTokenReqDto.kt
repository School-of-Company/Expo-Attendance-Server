package team.startup.expo.domain.qr.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "분실 종이 QR 무효화 요청")
data class RevokeQrTokenReqDto(
    @field:Schema(
        description = "종이에 인쇄된 코드(대소문자 무관). 끝 두 자리 확인 문자가 맞아야 한다",
        example = "ADT-0001-K7",
        minLength = 1,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @field:NotBlank
    @field:Size(max = 20)
    val code: String,
)
