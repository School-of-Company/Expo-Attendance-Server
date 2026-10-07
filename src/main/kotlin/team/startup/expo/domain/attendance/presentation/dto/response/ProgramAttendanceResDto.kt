package team.startup.expo.domain.attendance.presentation.dto.response

/** 시각은 `HH:mm`이다. 퇴실은 기록하지 않아 `leaveTime`은 항상 `null`이다(응답 형식 유지). 미출석자는 목록에 없다. */
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
