package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.Positive

data class GetParticipantQrReqDto(
    /** 신청 서비스가 대표자 세션으로 확인한 대표자 ID. 유저 서비스가 이 대표자의 일행인지 확인한다. */
    @field:Positive
    val representativeId: Long,
)
