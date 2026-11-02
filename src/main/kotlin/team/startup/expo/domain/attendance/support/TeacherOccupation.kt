package team.startup.expo.domain.attendance.support

/**
 * 교원·예비교사 직업 코드(유저 서비스의 `occupation`). 같은 집합을 두 규칙이 쓴다.
 * - 명찰: 소속을 받아 "소속 이름"으로 찍는 일반 참가자다([EntryBadge]).
 * - 당일 입장: 열린 회차가 없어도 오늘 확정 회차로 시간과 관계없이 입장한다([PreregisterSessionValidator.check], #283).
 */
object TeacherOccupation {
    private val CODES = setOf("TEACHER", "PRE_SERVICE_TEACHER")

    fun matches(occupation: String?): Boolean = occupation in CODES
}
