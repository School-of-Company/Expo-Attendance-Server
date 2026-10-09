package team.startup.expo.domain.qr.entity

/**
 * 종이 QR을 받는 사람의 구분. 현장에서는 미리 인쇄한 종이를 문답 없이 빨리 나눠 주려고 `ADULT`와 `CHILD` 두 가지(바쁘면
 * `GENERAL` 한 가지)만 쓴다. 나머지 값은 폼 서비스의 직업(`Occupation`) 값과 같아, 구분을 더 잘게 나눠야 할 때 코드를
 * 바꾸지 않고 쓸 수 있다.
 */
enum class QrCategory {
    KINDERGARTEN_STUDENT,
    ELEMENTARY_STUDENT,
    MIDDLE_SCHOOL_STUDENT,
    HIGH_SCHOOL_STUDENT,
    SCHOOL_STAFF,
    PRE_SERVICE_TEACHER,
    PARENT,
    GENERAL,
    TEACHER,
    ADULT,
    CHILD,
}
