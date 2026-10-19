package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.AssignPreregisterSessionReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CancelPreregisterSessionReqDto

interface PreregisterSessionService {
    /**
     * 확정·승급한 참가자의 회차 하나를 기록한다. 참가자의 다른 회차는 그대로 두고, 이 회차가 취소 상태였다면 되살린다(재신청).
     * `revision`이 있으면 이 회차에 이미 받은 값(또는 회차를 지정하지 않은 취소의 값)보다 작은 요청은 무시하고,
     * 같은 값이면 내용이 같을 때만 멱등이다(다르면 409).
     */
    fun assign(
        expoId: String,
        participantId: Long,
        reqDto: AssignPreregisterSessionReqDto,
    )

    /**
     * 취소된 신청으로 표시해 QR 입장을 막는다. `sessionId`가 있으면 그 회차만, 없으면 참가자의 모든 회차를 취소한다.
     * `revision`이 없으면 기록이 없어도 성공하고 아무것도 만들지 않는다.
     * `revision`이 있으면 기록이 없어도 취소를 남기고, assign과 같은 순서 규칙을 따른다.
     */
    fun cancel(
        expoId: String,
        participantId: Long,
        reqDto: CancelPreregisterSessionReqDto,
    )
}
