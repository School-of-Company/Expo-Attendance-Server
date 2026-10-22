package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 프로그램 출석 스캔 설정.
 *
 * @property requireCode 켜면 일반 프로그램 스캔에 참가자 `code`가 반드시 있어야 한다. 끄면 `code`가 없는 이전 앱의 요청도
 * 참가자 ID만으로 받는다(`code`가 있으면 항상 확인한다). 앱이 `code`를 보내게 된 뒤에 켠다.
 * @property leadMinutes 일반 프로그램 입실(첫 스캔)을 시작 몇 분 전부터 받을지(기본 10). 종료 시각까지 받고, 퇴실은 시간대를 보지 않는다(#198).
 */
@ConfigurationProperties(prefix = "program-attendance")
data class ProgramAttendanceProperties(
    val requireCode: Boolean = false,
    val leadMinutes: Long = 10,
)
