package team.startup.expo.domain.attendance.presentation.dto.response

/** 이미 입장했거나 프로그램에 출석한 참가자라 취소 보호를 걸지 않았다는 응답(409). */
data class CancellationGuardBlockedResDto(
    val entered: Boolean,
    val programAttended: Boolean,
)
