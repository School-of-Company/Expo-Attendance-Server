package team.startup.expo.domain.attendance.entity

enum class OutboxStatus {
    PENDING,
    PUBLISHED,

    /** 이벤트 자체의 문제로 다시 보내도 실패해서 발행을 포기한 이벤트. 보관 기간이 지나면 지운다. */
    FAILED,
}
