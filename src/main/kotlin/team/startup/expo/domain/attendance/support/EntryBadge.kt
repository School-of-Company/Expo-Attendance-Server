package team.startup.expo.domain.attendance.support

import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto

/** 명찰 출력 규칙. 입장 스캔 응답과 입장 목록(#237)이 같은 규칙을 쓴다. */
object EntryBadge {
    /** 명찰 대상은 연수자 전원과 교사·예비교사인 일반 참가자다. 대상이 아니면 `null`이다. */
    fun of(
        trainee: Boolean,
        occupation: String?,
        name: String,
        school: String?,
    ): BadgeResDto? = if (trainee || TeacherOccupation.matches(occupation)) BadgeResDto(name = name, school = school) else null
}
