package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.qr.presentation.dto.request.GetQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.request.RevokeQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenListResDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenSummaryResDto
import team.startup.expo.domain.qr.service.GetQrTokenSummaryService
import team.startup.expo.domain.qr.service.GetQrTokensService
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.domain.qr.service.RevokeQrTokenService
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/qr-tokens")
class QrTokenController(
    private val issueQrTokensService: IssueQrTokensService,
    private val getQrTokenSummaryService: GetQrTokenSummaryService,
    private val getQrTokensService: GetQrTokensService,
    private val revokeQrTokenService: RevokeQrTokenService,
) {
    @Operation(
        summary = "종이 QR 토큰 발급",
        description = "박람회의 종이 QR 토큰을 count개(1~1000) 발급한다. 구분별 연번과 인쇄용 코드(예: `ADT-0001-K7`)를 함께 돌려준다. 관리자(`ROLE_ADMIN`) 전용이다.",
    )
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

    @Operation(summary = "종이 QR 발급 현황", description = "구분별로 사용 중인 수와 무효화한 수를 돌려준다. 관리자(`ROLE_ADMIN`) 전용이다. 읽기 전용이다.")
    @ApiError(401, "관리자 역할 헤더(`X-User-Id`, `X-User-Role`)가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}/summary")
    fun summary(
        @PathVariable expoId: String,
    ): QrTokenSummaryResDto = getQrTokenSummaryService.get(expoId)

    @Operation(
        summary = "종이 QR 다시 조회",
        description = "구분과 시작 연번, 개수(최대 120)로 이미 발급한 종이 QR의 토큰·코드를 연번 순서로 돌려준다. 다시 인쇄할 때 쓴다. 관리자(`ROLE_ADMIN`) 전용이다.",
    )
    @ApiError(400, "구분이 없거나 시작 연번·개수가 범위를 벗어났다.")
    @ApiError(401, "관리자 역할 헤더(`X-User-Id`, `X-User-Role`)가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}")
    fun list(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: GetQrTokensReqDto,
    ): QrTokenListResDto = getQrTokensService.get(expoId, reqDto)

    @Operation(
        summary = "분실 종이 QR 무효화",
        description = "종이에 인쇄된 코드로 QR을 사용 중지한다. 이후 그 QR의 입구 스캔은 \"사용이 중지된 QR\"로 거부된다. 이미 무효화한 코드도 성공으로 답한다. 관리자(`ROLE_ADMIN`) 전용이다.",
    )
    @ApiError(400, "코드 형식이나 확인 문자(끝 두 자리)가 올바르지 않다.")
    @ApiError(401, "관리자 역할 헤더(`X-User-Id`, `X-User-Role`)가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(404, "이 박람회에 그 코드의 QR이 없다(연번 코드가 생기기 전에 발급한 QR은 코드가 없다).")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{expoId}/revoke")
    fun revoke(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: RevokeQrTokenReqDto,
    ) {
        revokeQrTokenService.revoke(expoId, reqDto)
    }
}
