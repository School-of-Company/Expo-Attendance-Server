package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.ProgramType

/** 프로그램 하나의 출석 요약. 출석 기록이 없으면 `attendeeCount`는 0이고 시각은 `null`이다. */
@Schema(description = "프로그램 하나의 출석 요약. 출석 기록이 없으면 `attendeeCount`는 0이고 시각은 `null`이다")
data class ProgramAttendanceSummaryResDto(
    @field:Schema(description = "프로그램 유형", example = "STANDARD")
    val programType: ProgramType,
    @field:Schema(description = "프로그램 ID", example = "7")
    val programId: Long,
    /** 출석한 참가자(연수는 연수자) 수. 프로그램마다 한 사람은 한 번만 기록되므로 출석 건수와 같다. */
    @field:Schema(description = "출석한 참가자(연수는 연수자) 수. 한 사람은 프로그램마다 한 번만 기록되므로 출석 건수와 같다", example = "42")
    val attendeeCount: Long,
    /** 가장 이른·늦은 입실 시각(`HH:mm`). */
    @field:Schema(description = "가장 이른 입실 시각(`HH:mm`). 출석이 없으면 `null`", example = "09:05", nullable = true)
    val firstEntryTime: String?,
    @field:Schema(description = "가장 늦은 입실 시각(`HH:mm`). 출석이 없으면 `null`", example = "09:50", nullable = true)
    val lastEntryTime: String?,
)
