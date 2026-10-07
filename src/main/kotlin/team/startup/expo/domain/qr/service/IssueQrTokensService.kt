package team.startup.expo.domain.qr.service

import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto

interface IssueQrTokensService {
    fun issue(
        expoId: String,
        reqDto: IssueQrTokensReqDto,
    ): IssueQrTokensResDto
}
