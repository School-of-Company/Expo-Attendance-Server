package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "발급된 종이 QR 토큰")
data class IssueQrTokensResDto(
    @field:Schema(
        description = "발급된 토큰 목록(요청한 수만큼). 로그에 남기지 않는다",
        example = "[\"sample-token-not-real-1\", \"sample-token-not-real-2\"]",
    )
    val tokens: List<String>,
) {
    override fun toString() = "IssueQrTokensResDto(tokens=***)"
}
