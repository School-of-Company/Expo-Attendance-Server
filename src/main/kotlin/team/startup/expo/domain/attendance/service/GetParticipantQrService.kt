package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto

interface GetParticipantQrService {
    /**
     * 입장할 수 있는 참가자의 QR 값을 돌려준다. 신청 서비스가 대표자 소유권과 확정 여부를 확인한 뒤 부른다.
     * 회차 기록이 없는 참가자(현장등록)는 회차를 확인하지 않는다. 회차가 모두 취소된 참가자, 유저 서비스가 모르는 참가자는 구분 없이 404다.
     */
    fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto
}
