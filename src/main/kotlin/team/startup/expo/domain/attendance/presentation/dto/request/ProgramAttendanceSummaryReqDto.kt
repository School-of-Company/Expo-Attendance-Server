package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import team.startup.expo.domain.attendance.entity.ProgramType

@Schema(description = "프로그램별 출석 요약 요청")
data class ProgramAttendanceSummaryReqDto(
    @field:Schema(description = "출석 요약을 볼 프로그램 목록(1~500개). 중복은 한 번만 처리하고 응답은 요청 순서를 따른다")
    @field:Valid
    @field:NotEmpty
    @field:Size(max = 500)
    val programs: List<ProgramRefReqDto>,
)

/** 일반 프로그램과 연수 프로그램은 ID 공간이 겹칠 수 있어 유형을 함께 보낸다. */
@Schema(description = "프로그램 참조(유형과 ID)")
data class ProgramRefReqDto(
    @field:Schema(description = "프로그램 유형. 일반 프로그램과 연수 프로그램은 ID 공간이 겹칠 수 있어 함께 보낸다", example = "STANDARD")
    val programType: ProgramType,
    @field:Schema(description = "프로그램 ID", example = "7")
    @field:Positive
    val programId: Long,
)
