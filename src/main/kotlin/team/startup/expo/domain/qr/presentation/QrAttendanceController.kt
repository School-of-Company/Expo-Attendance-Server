package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.support.ScannerName
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.response.ScanQrTokenResDto
import team.startup.expo.domain.qr.service.ScanQrTokenService
import team.startup.expo.global.logging.RequestContext
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/attendance/qr")
class QrAttendanceController(
    private val scanQrTokenService: ScanQrTokenService,
) {
    @Operation(
        summary = "종이 QR 입구 스캔",
        description =
            "진행 중인 박람회에서 토큰의 오늘 입장을 기록한다. 오늘 처음이면 `FIRST`, 퇴장한 뒤 다시 찍으면 재입장(`RE_ENTRY`)이다. " +
                "진행 중이 아니면 400, 퇴장하지 않고 같은 날 다시 찍으면 400, 없는 토큰·다른 박람회 토큰은 404다.",
    )
    @ApiError(400, "요청 값이 올바르지 않거나, 박람회가 진행 중인 상태가 아니거나, 오늘 이미 입장했고 퇴장하지 않은 토큰이다(퇴장 스캔 뒤에는 재입장).")
    @ApiError(404, "박람회를 찾지 못했거나, 없는 토큰이거나 다른 박람회의 토큰이다. 박람회 서비스가 응답하지 못하면 오류 없이 기록하고 나중에 다시 확인한다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @PatchMapping("/{expoId}")
    fun scan(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanQrTokenReqDto,
        @RequestHeader(name = ScannerName.HEADER, required = false) scannerName: String?,
    ): ScanQrTokenResDto {
        RequestContext.bindScan(expoId, null)
        return scanQrTokenService.scan(expoId, reqDto, scannerName)
    }
}
