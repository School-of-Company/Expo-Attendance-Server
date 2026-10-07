package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto
import team.startup.expo.domain.attendance.service.ScanStandardProgramService
import team.startup.expo.domain.attendance.service.ScanTrainingProgramService

@RestController
@RequestMapping("/attendance")
class ProgramAttendanceController(
    private val scanStandardProgramService: ScanStandardProgramService,
    private val scanTrainingProgramService: ScanTrainingProgramService,
) {
    @Operation(
        summary = "일반 프로그램 출석 스캔",
        description = "첫 스캔은 입실, 두 번째는 퇴실을 기록한다. 퇴실한 뒤나 진행 기간이 아니면 400, 프로그램·참가자가 없거나 신청하지 않았으면 404다.",
    )
    @PatchMapping("/standard/{programId}")
    fun scanStandard(
        @PathVariable programId: Long,
        @Valid @RequestBody reqDto: ScanStandardProgramReqDto,
    ) = scanStandardProgramService.scan(programId, reqDto)

    @Operation(
        summary = "연수 프로그램 출석 스캔",
        description = "첫 스캔은 입실, 두 번째는 퇴실을 기록한다. 퇴실한 뒤나 진행 기간이 아니면 400, 프로그램·연수자가 없거나 신청하지 않았으면 404다.",
    )
    @PatchMapping("/training/{programId}")
    fun scanTraining(
        @PathVariable programId: Long,
        @Valid @RequestBody reqDto: ScanTrainingProgramReqDto,
    ) = scanTrainingProgramService.scan(programId, reqDto)
}
