package team.startup.expo.domain.attendance.presentation.dto.response

/** 시각은 `HH:mm`이고 퇴실 전에는 `leaveTime`이 `null`이다. 미출석자는 목록에 없다. */
data class StandardProgramAttendanceResDto(
    val participantId: Long,
    val entryTime: String,
    val leaveTime: String?,
)

data class TrainingProgramAttendanceResDto(
    val traineeId: Long,
    val entryTime: String,
    val leaveTime: String?,
)
