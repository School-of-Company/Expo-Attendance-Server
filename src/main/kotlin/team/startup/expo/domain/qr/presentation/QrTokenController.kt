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
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/qr-tokens")
class QrTokenController(
    private val issueQrTokensService: IssueQrTokensService,
) {
    @Operation(summary = "종이 QR 토큰 발급", description = "박람회의 종이 QR 토큰을 count개(1~1000) 발급한다. 관리자(`ROLE_ADMIN`) 전용이다.")
    @ApiError(400, "요청 값이 올바르지 않거나 박람회 ID 형식(소문자 UUID)이 아니다.")
    @ApiError(401, "관리자 역할 헤더(`X-User-Id`, `X-User-Role`)가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(404, "삭제된 박람회다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{expoId}")
    fun issue(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: IssueQrTokensReqDto,
    ): IssueQrTokensResDto = issueQrTokensService.issue(expoId, reqDto)
}
