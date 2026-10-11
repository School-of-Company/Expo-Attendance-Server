package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "신청 취소 표시 요청(쿼리 파라미터)")
data class CancelPreregisterSessionReqDto(
    /** 참가자별로 늘어나는 순서 값. 있으면 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다. */
    @field:Schema(
        description = "참가자별로 늘어나는 순서 값. 보내면 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다(없으면 기록이 있을 때만 취소)",
        example = "4",
        nullable = true,
    )
    @field:Positive
    val revision: Long? = null,
)
