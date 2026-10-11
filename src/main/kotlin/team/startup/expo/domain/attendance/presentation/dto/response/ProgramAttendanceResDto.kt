package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/** 시각은 `HH:mm`이다. 퇴실은 기록하지 않아 `leaveTime`은 항상 `null`이다(응답 형식 유지). 미출석자는 목록에 없다. */
@Schema(description = "일반 프로그램 출석 기록. 미출석자는 목록에 없다")
data class StandardProgramAttendanceResDto(
    @field:Schema(description = "참가자 ID", example = "55")
    val participantId: Long,
    @field:Schema(description = "입실 시각(`HH:mm`)", example = "09:30")
    val entryTime: String,
    @field:Schema(description = "퇴실 시각. 퇴실은 기록하지 않아 항상 `null`이다", nullable = true)
    val leaveTime: String?,
)

@Schema(description = "연수 프로그램 출석 기록. 미출석자는 목록에 없다")
data class TrainingProgramAttendanceResDto(
    @field:Schema(description = "연수자 ID", example = "31")
    val traineeId: Long,
    @field:Schema(description = "입실 시각(`HH:mm`)", example = "09:30")
    val entryTime: String,
    @field:Schema(description = "퇴실 시각. 퇴실은 기록하지 않아 항상 `null`이다", nullable = true)
    val leaveTime: String?,
)
