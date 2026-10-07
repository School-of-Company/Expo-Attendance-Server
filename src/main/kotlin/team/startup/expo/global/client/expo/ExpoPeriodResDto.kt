package team.startup.expo.global.client.expo

/** 박람회 서비스 `GET /internal/expo/{expoId}` 응답. 날짜는 `yyyy-MM-dd` 문자열이다. */
data class ExpoPeriodResDto(
    val startedDay: String,
    val finishedDay: String,
)
