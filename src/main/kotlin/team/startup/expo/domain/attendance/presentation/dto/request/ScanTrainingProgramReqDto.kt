package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ScanTrainingProgramReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Positive
    val traineeId: Long,
)
