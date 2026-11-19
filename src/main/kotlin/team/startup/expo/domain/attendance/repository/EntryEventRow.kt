package team.startup.expo.domain.attendance.repository

import java.time.LocalDate
import java.time.LocalDateTime

/** [EntryLogRepository.findEvents]의 한 행. 이름은 쿼리의 열 별칭과 같다. */
interface EntryEventRow {
    val occurredAt: LocalDateTime
    val eventType: String
    val subjectType: String

    /** 종이 QR이면 토큰이라 응답에 싣지 않는다. */
    val subjectId: String

    /** 입장 로그의 구분(종이 QR만 의미가 있다). */
    val category: String?

    /** 종이 QR의 인쇄 코드(V17 이전 토큰은 없다). */
    val paperCode: String?
    val scanner: String?
    val id: Long
    val attendanceDate: LocalDate
}
