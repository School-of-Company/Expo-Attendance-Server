package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.qr.entity.QrCategory
import team.startup.expo.domain.qr.entity.QrToken

@Schema(description = "종이 QR 한 장")
data class QrTokenItemResDto(
    @field:Schema(description = "구분", example = "ADULT", requiredMode = Schema.RequiredMode.REQUIRED)
    val category: QrCategory,
    @field:Schema(description = "구분별 연번(1부터)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    val serial: Int,
    @field:Schema(description = "종이에 인쇄하는 코드. 끝 두 자리는 확인 문자다", example = "ADT-0001-K7", requiredMode = Schema.RequiredMode.REQUIRED)
    val code: String,
    @field:Schema(description = "QR에 담는 토큰. 로그에 남기지 않는다", example = "sample-token-not-real", requiredMode = Schema.RequiredMode.REQUIRED)
    val token: String,
    @field:Schema(description = "분실 등으로 사용을 중지했는지", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    val revoked: Boolean,
) {
    override fun toString() = "QrTokenItemResDto(category=$category, serial=$serial, code=$code, revoked=$revoked)"

    companion object {
        /** 연번 코드가 있는 토큰만 바꿀 수 있다. */
        fun from(token: QrToken) =
            QrTokenItemResDto(
                category = token.category,
                serial = requireNotNull(token.serial),
                code = requireNotNull(token.code),
                token = token.token,
                revoked = token.revokedAt != null,
            )
    }
}
