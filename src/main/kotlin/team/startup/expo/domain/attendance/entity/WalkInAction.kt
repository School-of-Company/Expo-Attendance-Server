package team.startup.expo.domain.attendance.entity

/** 간편입장 버튼의 동작. `UNDO`는 잘못 누른 것을 하나 되돌린다. */
enum class WalkInAction(
    val delta: Int,
) {
    ADD(1),
    UNDO(-1),
}
