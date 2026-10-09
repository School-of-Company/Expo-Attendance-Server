package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto
import team.startup.expo.domain.qr.service.IssueQrTokensService

@RestController
@RequestMapping("/qr-tokens")
class QrTokenController(
    private val issueQrTokensService: IssueQrTokensService,
) {
    @Operation(summary = "종이 QR 토큰 발급", description = "박람회의 종이 QR 토큰을 count개(1~1000) 발급한다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{expoId}")
    fun issue(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: IssueQrTokensReqDto,
    ): IssueQrTokensResDto = issueQrTokensService.issue(expoId, reqDto)
}
