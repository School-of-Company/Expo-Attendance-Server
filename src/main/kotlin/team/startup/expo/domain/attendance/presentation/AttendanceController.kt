package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.global.logging.RequestContext
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/attendance")
class AttendanceController(
    private val scanEntryService: ScanEntryService,
) {
    @Operation(
        summary = "박람회 입장 스캔",
        description =
            "박람회 진행 기간을 확인하고 참가자의 오늘 입장을 기록한다. 오늘 이미 입장했거나 진행 기간이 아니면 400, 박람회·참가자가 없으면 404다." +
                " 입장이 성공한 직후 같은 스캐너 사용자가 같은 QR을 다시 찍으면 처음 응답을 돌려준다(응답 유실 대비, 기본 5분).",
    )
    @ApiError(400, "요청 값이 올바르지 않다(일반 참가자는 전화번호 또는 참가자 ID와 코드, 연수자는 전화번호가 필요하다).")
    @ApiError(400, "박람회가 진행 중인 상태가 아니다.")
    @ApiError(400, "오늘 이미 입장했다.")
    @ApiError(400, "사전등록 QR이 취소된 신청이거나 입장 시간(회차 시작 전 설정 시간부터 종료까지)이 아니다.")
    @ApiError(404, "박람회를 찾지 못했다.")
    @ApiError(404, "참가자(연수자)를 찾지 못했다. 코드가 틀린 경우도 같다.")
    @ApiError(409, "신청 취소를 처리 중이다.")
    @ApiError(503, "박람회·유저 서비스를 사용할 수 없거나, 취소 처리와 겹쳐 잠시 후 다시 시도해야 한다.")
    @PatchMapping("/{expoId}")
    fun scanEntry(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanEntryReqDto,
        @RequestHeader(name = "X-User-Id", required = false) scannerId: String?,
    ): ScanEntryResDto {
        RequestContext.bindScan(expoId, reqDto.participantId)
        return scanEntryService.scan(expoId, reqDto, scannerId)
    }
}
