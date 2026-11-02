package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto

interface GetParticipantQrService {
    /**
     * 참가자의 QR 값을 돌려준다. 신청 서비스가 대표자 소유권과 확정 여부를 확인한 뒤 부른다(#218).
     * 확정 회차가 하나라도 있으면 방문일 전에도 돌려주고, 오늘 ACTIVE 현장등록도 돌려준다. QR을 보여 주는 것과 입장 허용은 별개다.
     * 회차 기록이 없거나 모두 취소된 참가자, 다른 날·날짜 미상 현장등록만 있는 참가자, 유저 서비스가 모르는 참가자는 구분 없이 404다.
     */
    fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto
}
