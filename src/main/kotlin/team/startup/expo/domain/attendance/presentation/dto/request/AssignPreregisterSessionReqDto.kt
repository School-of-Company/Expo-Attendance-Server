package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.Positive

data class AssignPreregisterSessionReqDto(
    @field:Positive
    val sessionId: Long,
)
