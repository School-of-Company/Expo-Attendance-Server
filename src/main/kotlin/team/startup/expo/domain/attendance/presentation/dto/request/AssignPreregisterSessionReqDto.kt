package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "신청 회차 기록 요청")
data class AssignPreregisterSessionReqDto(
    @field:Schema(description = "참가자가 신청한 회차 ID(박람회 서비스의 회차). 다른 회차 기록은 바꾸지 않는다", example = "12")
    @field:Positive
    val sessionId: Long,
    /** 회차별로 늘어나는 순서 값. 있으면 이 회차에 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다. */
    @field:Schema(
        description = "회차별로 늘어나는 순서 값(참가자 단위로 늘려 보내도 된다). 보내면 이 회차에 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다(없으면 항상 반영)",
        example = "3",
        nullable = true,
    )
    @field:Positive
    val revision: Long? = null,
)
