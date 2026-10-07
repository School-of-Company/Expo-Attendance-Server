package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
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
        description = "박람회 진행 기간을 확인하고 참가자의 오늘 입장을 기록한다. 오늘 이미 입장했거나 진행 기간이 아니면 400, 박람회·참가자가 없으면 404다.",
    )
    @PatchMapping("/{expoId}")
    fun scanEntry(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanEntryReqDto,
    ): ScanEntryResDto = scanEntryService.scan(expoId, reqDto)
}
