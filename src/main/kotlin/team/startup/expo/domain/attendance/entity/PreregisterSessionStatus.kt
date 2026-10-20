package team.startup.expo.domain.attendance.entity

enum class PreregisterSessionStatus {
    /** 확정되었거나 대기에서 승급한 신청. 입장 때 회차 시간을 확인한다. */
    ACTIVE,

    /** 취소된 신청. QR로 입장할 수 없다. */
    CANCELLED,

    /** 취소한 뒤 현장등록으로 다시 등록했다(Application #249). 회차 확인 대상이 아니라 현장등록자처럼 입장하고, revision은 순서 비교에 남긴다. */
    FIELD_REGISTERED,
}
