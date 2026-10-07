package team.startup.expo.domain.qr.presentation.dto.response

data class IssueQrTokensResDto(
    val tokens: List<String>,
) {
    override fun toString() = "IssueQrTokensResDto(tokens=***)"
}
