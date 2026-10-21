package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/** 날짜는 첫 스캔(입실)을 기록한 실제 날짜(`yyyy-MM-dd`), 시각은 `HH:mm`이고 퇴실 전에는 `leaveTime`이 `null`이다. 미출석자는 목록에 없다. */
@Schema(description = "일반 프로그램 출석 기록. 미출석자는 목록에 없다")
data class StandardProgramAttendanceResDto(
    @field:Schema(description = "참가자 ID", example = "55")
    val participantId: Long,
    @field:Schema(description = "실제 출석(입실 기록) 날짜. 프로그램 예정일이 아니다", example = "2026-03-02")
    val attendanceDate: LocalDate,
    @field:Schema(description = "입실 시각(`HH:mm`)", example = "09:30")
    val entryTime: String,
    @field:Schema(description = "퇴실 시각(`HH:mm`). 퇴실 스캔 전에는 `null`이다", nullable = true)
    val leaveTime: String?,
)

@Schema(description = "연수 프로그램 출석 기록. 미출석자는 목록에 없다")
data class TrainingProgramAttendanceResDto(
    @field:Schema(description = "연수자 ID", example = "31")
    val traineeId: Long,
    @field:Schema(description = "실제 출석(입실 기록) 날짜. 프로그램 예정일이 아니다", example = "2026-03-02")
    val attendanceDate: LocalDate,
    @field:Schema(description = "입실 시각(`HH:mm`)", example = "09:30")
    val entryTime: String,
    @field:Schema(description = "퇴실 시각(`HH:mm`). 퇴실 스캔 전에는 `null`이다", nullable = true)
    val leaveTime: String?,
)
