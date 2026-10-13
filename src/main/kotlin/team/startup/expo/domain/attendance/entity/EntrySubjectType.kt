package team.startup.expo.domain.attendance.entity

/** 입장한 쪽의 종류. 일반 참가자와 연수자는 ID, 종이 QR은 토큰 값으로 가리킨다. */
enum class EntrySubjectType {
    STANDARD,
    TRAINEE,
    PAPER_QR,
}
