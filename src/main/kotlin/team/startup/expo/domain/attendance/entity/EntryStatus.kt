package team.startup.expo.domain.attendance.entity

/** 입장 스캔 결과의 상태(#148). 같은 날 퇴장한 뒤 다시 입장하면 재입장이다. */
enum class EntryStatus {
    FIRST,
    RE_ENTRY,
}
