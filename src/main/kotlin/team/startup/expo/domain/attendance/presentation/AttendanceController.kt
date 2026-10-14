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
import team.startup.expo.domain.attendance.presentation.dto.request.ScanExitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanExitResDto
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.domain.attendance.service.ScanExitService
import team.startup.expo.domain.attendance.service.ScannerName
import team.startup.expo.global.logging.RequestContext
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/attendance")
class AttendanceController(
    private val scanEntryService: ScanEntryService,
    private val scanExitService: ScanExitService,
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
        @RequestHeader(name = ScannerName.HEADER, required = false) scannerName: String?,
    ): ScanEntryResDto {
        RequestContext.bindScan(expoId, reqDto.participantId)
        return scanEntryService.scan(expoId, reqDto, scannerId, scannerName)
    }

    @Operation(
        summary = "박람회 퇴장 스캔",
        description =
            "오늘 입장한 참가자·연수자·종이 QR의 퇴장 시각을 기록한다. 입장하지 않았거나(입장 로그가 생기기 전의 입장 포함) 이미 퇴장했으면 400이다. " +
                "재입장은 아직 허용하지 않는다(입장은 하루 한 번). 기간이나 회차는 확인하지 않는다.",
    )
    @ApiError(400, "참가자 ID와 코드, 연수자 ID, 종이 QR 토큰 중 하나만 보내야 한다.")
    @ApiError(400, "오늘 입장 기록이 없다.")
    @ApiError(400, "오늘 이미 퇴장 처리된 QR이다.")
    @ApiError(404, "참가자(연수자)를 찾지 못했다. 코드가 틀린 경우도 같다.")
    @ApiError(404, "종이 QR 토큰을 찾을 수 없다(다른 박람회의 토큰 포함).")
    @ApiError(503, "유저 서비스를 사용할 수 없다.")
    @PatchMapping("/exit/{expoId}")
    fun scanExit(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanExitReqDto,
        @RequestHeader(name = ScannerName.HEADER, required = false) scannerName: String?,
    ): ScanExitResDto {
        RequestContext.bindScan(expoId, reqDto.participantId)
        return scanExitService.scan(expoId, reqDto, scannerName)
    }
}
