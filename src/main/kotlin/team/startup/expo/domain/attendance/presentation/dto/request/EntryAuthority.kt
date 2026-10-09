package team.startup.expo.domain.attendance.presentation.dto.request

/** v1 `Authority`의 입장 대상 값. 유저 서비스의 `participationType`으로 바뀌어 전달된다. */
enum class EntryAuthority(
    val participationType: String,
) {
    ROLE_STANDARD("STANDARD"),
    ROLE_TRAINEE("TRAINEE"),
}
