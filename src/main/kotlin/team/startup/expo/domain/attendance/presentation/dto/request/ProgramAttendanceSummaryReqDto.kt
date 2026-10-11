package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import team.startup.expo.domain.attendance.entity.ProgramType

data class ProgramAttendanceSummaryReqDto(
    @field:Valid
    @field:NotEmpty
    @field:Size(max = 500)
    val programs: List<ProgramRefReqDto>,
)

/** 일반 프로그램과 연수 프로그램은 ID 공간이 겹칠 수 있어 유형을 함께 보낸다. */
data class ProgramRefReqDto(
    val programType: ProgramType,
    @field:Positive
    val programId: Long,
)
