package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "발급된 종이 QR 토큰")
data class IssueQrTokensResDto(
    @field:Schema(
        description = "발급된 토큰 목록(요청한 수만큼). 로그에 남기지 않는다. `items`의 `token`과 같은 순서다",
        example = "[\"sample-token-not-real-1\", \"sample-token-not-real-2\"]",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val tokens: List<String>,
    @field:Schema(description = "발급된 토큰의 연번과 인쇄용 코드(연번 순서)", requiredMode = Schema.RequiredMode.REQUIRED)
    val items: List<QrTokenItemResDto>,
) {
    override fun toString() = "IssueQrTokensResDto(tokens=***)"
}
