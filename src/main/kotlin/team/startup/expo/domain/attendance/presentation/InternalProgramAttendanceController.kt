package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.response.StandardProgramAttendanceResDto
import team.startup.expo.domain.attendance.presentation.dto.response.TrainingProgramAttendanceResDto
import team.startup.expo.domain.attendance.service.GetProgramAttendancesService

/** 박람회 서비스가 프로그램 신청자 목록에 출석 시간을 합칠 때 부른다. 출석하지 않은 사람은 목록에 없다. */
@RestController
@RequestMapping("/internal/program-attendances")
class InternalProgramAttendanceController(
    private val getProgramAttendancesService: GetProgramAttendancesService,
) {
    @Operation(summary = "일반 프로그램 출석 시간 조회", description = "프로그램 ID로 입·퇴실 시각(`HH:mm`)을 조회한다.")
    @GetMapping("/standard/{programId}")
    fun getStandard(
        @PathVariable programId: Long,
    ): List<StandardProgramAttendanceResDto> = getProgramAttendancesService.getStandard(programId)

    @Operation(summary = "연수 프로그램 출석 시간 조회", description = "프로그램 ID로 입·퇴실 시각(`HH:mm`)을 조회한다.")
    @GetMapping("/training/{programId}")
    fun getTraining(
        @PathVariable programId: Long,
    ): List<TrainingProgramAttendanceResDto> = getProgramAttendancesService.getTraining(programId)
}
