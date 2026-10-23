package team.startup.expo.domain.attendance.support

/**
 * 입구 스캐너(기기)가 `X-Scanner-Name` 헤더로 보내는 이름(#131). 클라이언트가 기기별로 저장한 짧은 이름이다.
 * 스캔을 막지 않도록 잘못된 값은 거절하지 않고 다듬어 쓰며, 비면 기록하지 않는다.
 */
object ScannerName {
    const val HEADER = "X-Scanner-Name"
    const val MAX_LENGTH = 40

    fun of(raw: String?): String? =
        raw
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.take(MAX_LENGTH)
            ?.takeIf { it.isNotEmpty() }
}
