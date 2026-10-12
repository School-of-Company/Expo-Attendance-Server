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
import team.startup.expo.global.logging.RequestContext
import team.startup.expo.global.swagger.ApiError

@RestController
@RequestMapping("/attendance")
class ProgramAttendanceController(
    private val scanStandardProgramService: ScanStandardProgramService,
    private val scanTrainingProgramService: ScanTrainingProgramService,
) {
    @Operation(
        summary = "일반 프로그램 출석 스캔",
        description = "첫 스캔은 입실을 기록하고 다시 찍어도 변하지 않는다(퇴실은 기록하지 않는다). 진행 기간이 아니면 400, 프로그램·참가자가 없거나 신청하지 않았으면 404다.",
    )
    @ApiError(400, "요청 값이 올바르지 않거나, 박람회가 진행 중인 상태가 아니거나, 참가자 코드가 필요한데(`program-attendance.require-code`) 없거나, 취소된 신청이다.")
    @ApiError(404, "프로그램을 찾지 못했거나, 참가자를 찾지 못했거나(코드가 틀린 경우 포함), 프로그램을 신청하지 않았다.")
    @ApiError(409, "신청 취소를 처리 중이다.")
    @ApiError(503, "박람회·유저·신청 서비스를 사용할 수 없거나, 취소 처리와 겹쳐 잠시 후 다시 시도해야 한다.")
    @PatchMapping("/standard/{programId}")
    fun scanStandard(
        @PathVariable programId: Long,
        @Valid @RequestBody reqDto: ScanStandardProgramReqDto,
    ) {
        RequestContext.bindScan(reqDto.expoId, reqDto.participantId)
        scanStandardProgramService.scan(programId, reqDto)
    }

    @Operation(
        summary = "연수 프로그램 출석 스캔",
        description = "첫 스캔은 입실을 기록하고 다시 찍어도 변하지 않는다(퇴실은 기록하지 않는다). 진행 기간이 아니면 400, 프로그램·연수자가 없거나 신청하지 않았으면 404다.",
    )
    @ApiError(
        400,
        "요청 값이 올바르지 않거나, `traineeId`도 `participantId`와 `code`도 보내지 않았거나, `participantId`와 `code` 중 하나만 보냈거나, 박람회가 진행 중인 상태가 아니거나, 취소된 신청이다.",
    )
    @ApiError(404, "프로그램을 찾지 못했거나, 연수자를 찾지 못했거나(연결된 연수자가 없거나 코드가 틀린 경우 포함), 프로그램을 신청하지 않았다.")
    @ApiError(409, "신청 취소를 처리 중이다.")
    @ApiError(503, "박람회·유저·신청 서비스를 사용할 수 없거나, 취소 처리와 겹쳐 잠시 후 다시 시도해야 한다.")
    @PatchMapping("/training/{programId}")
    fun scanTraining(
        @PathVariable programId: Long,
        @Valid @RequestBody reqDto: ScanTrainingProgramReqDto,
    ) {
        RequestContext.bindScan(reqDto.expoId, reqDto.participantId)
        scanTrainingProgramService.scan(programId, reqDto)
    }
}
