package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "신청 취소 표시 요청(쿼리 파라미터)")
data class CancelPreregisterSessionReqDto(
    /** 취소할 회차. 없으면 참가자의 모든 회차를 취소한다(기존 방식). */
    @field:Schema(
        description = "취소할 회차 ID. 보내면 그 회차만 취소하고 다른 회차는 그대로 둔다. 없으면 참가자의 모든 회차를 취소한다(기존 방식)",
        example = "12",
        nullable = true,
    )
    @field:Positive
    val sessionId: Long? = null,
    /** 회차별로 늘어나는 순서 값. 있으면 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다. */
    @field:Schema(
        description = "회차별로 늘어나는 순서 값(참가자 단위로 늘려 보내도 된다). 보내면 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다(없으면 기록이 있을 때만 취소)",
        example = "4",
        nullable = true,
    )
    @field:Positive
    val revision: Long? = null,
)
