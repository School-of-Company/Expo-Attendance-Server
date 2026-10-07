package team.startup.expo.domain.qr.entity

/** Form-Server가 정의한 응답자 구분과 같은 값이다. 종이 QR을 받는 사람이 어떤 구분인지 담는다. */
enum class QrCategory {
    ELEMENTARY_STUDENT,
    MIDDLE_SCHOOL_STUDENT,
    HIGH_SCHOOL_STUDENT,
    SCHOOL_STAFF,
    PRE_SERVICE_TEACHER,
    PARENT,
    GENERAL,
    TEACHER,
}
