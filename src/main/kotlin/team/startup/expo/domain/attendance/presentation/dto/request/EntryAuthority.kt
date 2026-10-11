package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema

/** v1 `Authority`의 입장 대상 값. 유저 서비스의 `participationType`으로 바뀌어 전달된다. */
@Schema(description = "입장 대상. `ROLE_STANDARD`는 일반 참가자, `ROLE_TRAINEE`는 연수자")
enum class EntryAuthority(
    val participationType: String,
) {
    ROLE_STANDARD("STANDARD"),
    ROLE_TRAINEE("TRAINEE"),
}
