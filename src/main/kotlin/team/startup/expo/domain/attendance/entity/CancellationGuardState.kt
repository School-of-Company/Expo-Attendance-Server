package team.startup.expo.domain.attendance.entity

enum class CancellationGuardState {
    /** 취소를 검사하고 확정하는 중이다. 이 참가자의 입장·프로그램 스캔을 막는다. */
    HELD,

    /** 취소가 확정되었다. 재신청으로 회차가 다시 기록될 때까지 스캔을 막는다. */
    COMMITTED,
}
