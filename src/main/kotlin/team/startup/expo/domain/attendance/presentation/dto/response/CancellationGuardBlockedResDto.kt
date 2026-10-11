package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/** 이미 입장했거나 프로그램에 출석한 참가자라 취소 보호를 걸지 않았다는 응답(409). */
@Schema(description = "취소 보호를 걸지 못한 이유(409 응답 본문). 이미 입장했거나 프로그램에 출석한 참가자다")
data class CancellationGuardBlockedResDto(
    @field:Schema(description = "이미 박람회에 입장했는지", example = "true")
    val entered: Boolean,
    @field:Schema(description = "이미 프로그램에 출석했는지", example = "false")
    val programAttended: Boolean,
)
