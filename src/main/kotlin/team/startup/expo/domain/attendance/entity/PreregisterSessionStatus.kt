package team.startup.expo.domain.attendance.entity

enum class PreregisterSessionStatus {
    /** 확정되었거나 대기에서 승급한 신청. 입장 때 회차 시간을 확인한다. */
    ACTIVE,

    /** 취소된 신청. QR로 입장할 수 없다. */
    CANCELLED,
}
