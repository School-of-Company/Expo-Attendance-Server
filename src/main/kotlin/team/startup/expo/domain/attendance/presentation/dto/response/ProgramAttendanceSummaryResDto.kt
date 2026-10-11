package team.startup.expo.domain.attendance.presentation.dto.response

import team.startup.expo.domain.attendance.entity.ProgramType

/** 프로그램 하나의 출석 요약. 출석 기록이 없으면 `attendeeCount`는 0이고 시각은 `null`이다. */
data class ProgramAttendanceSummaryResDto(
    val programType: ProgramType,
    val programId: Long,
    /** 출석한 참가자(연수는 연수자) 수. 프로그램마다 한 사람은 한 번만 기록되므로 출석 건수와 같다. */
    val attendeeCount: Long,
    /** 가장 이른·늦은 입실 시각(`HH:mm`). */
    val firstEntryTime: String?,
    val lastEntryTime: String?,
)
