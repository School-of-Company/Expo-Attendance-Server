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
    @PatchMapping("/{expoId}")
    fun scanEntry(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanEntryReqDto,
        @RequestHeader(name = "X-User-Id", required = false) scannerId: String?,
    ): ScanEntryResDto = scanEntryService.scan(expoId, reqDto, scannerId)
}
