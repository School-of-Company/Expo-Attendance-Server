package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto

interface GetParticipantQrService {
    /**
     * 신청이 확정된 참가자의 QR 값을 돌려준다. 신청 서비스가 대표자 소유권을 확인한 뒤 부른다.
     * 회차 기록이 없거나 취소된 참가자, 유저 서비스가 모르는 참가자는 구분 없이 404다.
     */
    fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto
}
