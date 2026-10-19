package team.startup.expo.domain.attendance.entity

/**
 * QR 없는 방문객의 구분(#118, #149).
 *
 * `ADULT`, `STUDENT`는 이미 쌓인 기록과 기존 현장 화면이 쓰므로 유지한다. 새 화면은 참가 유형 8종만 쓴다.
 * 8종의 이름은 종이 QR 구분(`QrCategory`)과 같다.
 */
enum class WalkInCategory {
    ADULT,
    STUDENT,
    KINDERGARTEN_STUDENT,
    ELEMENTARY_STUDENT,
    MIDDLE_SCHOOL_STUDENT,
    HIGH_SCHOOL_STUDENT,
    PARENT,
    GENERAL,
    TEACHER,
    PRE_SERVICE_TEACHER,
}
