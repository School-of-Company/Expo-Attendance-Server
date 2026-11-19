package team.startup.expo.domain.attendance.support

import java.time.format.DateTimeFormatter

/** 응답에 싣는 시각 형식. 입·퇴장과 프로그램 출석 시각은 모두 분 단위 `HH:mm`이다. */
object TimeFormat {
    val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
