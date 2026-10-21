package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ProgramAttendanceSummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramAttendanceSummaryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.StandardProgramAttendanceResDto
import team.startup.expo.domain.attendance.presentation.dto.response.TrainingProgramAttendanceResDto
import team.startup.expo.domain.attendance.service.DeleteProgramAttendancesService
import team.startup.expo.domain.attendance.service.GetProgramAttendanceSummaryService
import team.startup.expo.domain.attendance.service.GetProgramAttendancesService
import team.startup.expo.global.swagger.ApiError

/** 박람회 서비스가 프로그램 신청자 목록에 출석 시간을 합칠 때 부른다. 출석하지 않은 사람은 목록에 없다. */
@RestController
@RequestMapping("/internal/program-attendances")
class InternalProgramAttendanceController(
    private val getProgramAttendancesService: GetProgramAttendancesService,
    private val deleteProgramAttendancesService: DeleteProgramAttendancesService,
    private val getProgramAttendanceSummaryService: GetProgramAttendanceSummaryService,
) {
    @Operation(summary = "일반 프로그램 출석 시간 조회", description = "프로그램 ID로 입·퇴실 시각(`HH:mm`)을 조회한다. 퇴실 전에는 `leaveTime`이 `null`이다.")
    @GetMapping("/standard/{programId}")
    fun getStandard(
        @PathVariable programId: Long,
    ): List<StandardProgramAttendanceResDto> = getProgramAttendancesService.getStandard(programId)

    @Operation(summary = "연수 프로그램 출석 시간 조회", description = "프로그램 ID로 입·퇴실 시각(`HH:mm`)을 조회한다. 퇴실 전에는 `leaveTime`이 `null`이다.")
    @GetMapping("/training/{programId}")
    fun getTraining(
        @PathVariable programId: Long,
    ): List<TrainingProgramAttendanceResDto> = getProgramAttendancesService.getTraining(programId)

    @Operation(
        summary = "프로그램별 출석 요약 조회",
        description =
            "프로그램(유형+ID) 목록의 출석 인원과 입실 시각 범위를 요청 순서대로 돌려줍니다(최대 500개, 중복은 한 번). " +
                "출석이 없으면 0과 null입니다. 프로그램이 어느 박람회 소속인지는 이 서비스가 모르므로 부르는 쪽이 확인합니다.",
    )
    @ApiError(400, "요청 값이 올바르지 않다(프로그램 목록이 비었거나 500개를 넘음 등).")
    @PostMapping("/summary")
    fun summarize(
        @Valid @RequestBody reqDto: ProgramAttendanceSummaryReqDto,
    ): List<ProgramAttendanceSummaryResDto> = getProgramAttendanceSummaryService.summarize(reqDto)

    @Operation(summary = "일반 프로그램 출석 정리", description = "프로그램이 삭제될 때 그 프로그램의 출석 기록을 지운다. 여러 번 불러도 결과는 같다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/standard/{programId}")
    fun deleteStandard(
        @PathVariable programId: Long,
    ) = deleteProgramAttendancesService.delete(ProgramType.STANDARD, programId)

    @Operation(summary = "연수 프로그램 출석 정리", description = "프로그램이 삭제될 때 그 프로그램의 출석 기록을 지운다. 여러 번 불러도 결과는 같다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/training/{programId}")
    fun deleteTraining(
        @PathVariable programId: Long,
    ) = deleteProgramAttendancesService.delete(ProgramType.TRAINING, programId)
}
