package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.EntrySubjectType

@Schema(description = "퇴장 스캔 결과")
data class ScanExitResDto(
    @field:Schema(description = "퇴장한 쪽의 종류", example = "STANDARD")
    val subjectType: EntrySubjectType,
    @field:Schema(description = "오늘 입장 시각(`HH:mm`)", example = "10:05")
    val enteredTime: String,
    @field:Schema(description = "퇴장 시각(`HH:mm`)", example = "11:40")
    val exitedTime: String,
    @field:Schema(description = "입장부터 퇴장까지의 분", example = "95")
    val stayedMinutes: Long,
)
