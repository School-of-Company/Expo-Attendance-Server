package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "취소 뒤 현장 재등록 요청")
data class FieldRegisterPreregisterSessionReqDto(
    @field:Schema(description = "회차 PUT·DELETE와 같은 순서 값. 이보다 오래된 취소만 풀고, 더 새로운 취소는 그대로 둔다", example = "8")
    @field:Positive
    val revision: Long,
)
