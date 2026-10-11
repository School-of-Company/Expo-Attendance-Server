package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "참가자 QR 값 조회 요청")
data class GetParticipantQrReqDto(
    /** 신청 서비스가 대표자 세션으로 확인한 대표자 ID. 유저 서비스가 이 대표자의 일행인지 확인한다. */
    @field:Schema(description = "신청 서비스가 대표자 세션으로 확인한 대표자 ID. 유저 서비스가 이 대표자의 일행인지 확인한다", example = "101")
    @field:Positive
    val representativeId: Long,
)
