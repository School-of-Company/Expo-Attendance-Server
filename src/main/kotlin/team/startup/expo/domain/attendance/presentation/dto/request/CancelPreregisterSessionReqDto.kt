package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.Positive

data class CancelPreregisterSessionReqDto(
    /** 참가자별로 늘어나는 순서 값. 있으면 이미 받은 값보다 작은 요청은 상태를 바꾸지 않는다. */
    @field:Positive
    val revision: Long? = null,
)
