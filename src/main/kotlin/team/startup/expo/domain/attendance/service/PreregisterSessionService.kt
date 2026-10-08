package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.AssignPreregisterSessionReqDto

interface PreregisterSessionService {
    /** 확정·승급한 참가자의 회차를 기록한다. 이미 있으면 바꾸고 취소 상태였다면 되살린다(재신청). */
    fun assign(
        expoId: String,
        participantId: Long,
        reqDto: AssignPreregisterSessionReqDto,
    )

    /** 취소된 신청으로 표시해 QR 입장을 막는다. 기록이 없어도 성공한다. */
    fun cancel(
        expoId: String,
        participantId: Long,
    )
}
