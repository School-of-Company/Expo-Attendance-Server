package team.startup.expo.domain.attendance.entity

/** QR 없는 방문객의 구분(#118). 종이 QR의 구분(`QrCategory`)과 달리 현장 화면의 두 버튼(성인, 어린이·학생)만 둔다. */
enum class WalkInCategory {
    ADULT,
    STUDENT,
}
