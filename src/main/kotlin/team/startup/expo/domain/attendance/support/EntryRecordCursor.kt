package team.startup.expo.domain.attendance.support

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

/**
 * 입장 기록 목록의 다음 페이지 위치(#250). 마지막으로 돌려준 이벤트의 (입장 날짜, 발생 시각, id)이고, 다음 페이지는 이보다 뒤의 이벤트다.
 * 호출자에게는 뜻을 감춘 문자열(base64url)로 주고 받는다. 형식이 틀리면 400이다.
 */
data class EntryRecordCursor(
    val attendanceDate: LocalDate,
    val occurredAt: LocalDateTime,
    val id: Long,
) {
    fun encode(): String = ENCODER.encodeToString("$attendanceDate|$occurredAt|$id".toByteArray())

    companion object {
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        fun decode(value: String): EntryRecordCursor =
            runCatching {
                val (date, at, id) = String(DECODER.decode(value)).split('|').also { require(it.size == 3) }
                EntryRecordCursor(LocalDate.parse(date), LocalDateTime.parse(at), id.toLong())
            }.getOrElse { throw ExpectedException(HttpStatus.BAD_REQUEST, "cursor가 올바르지 않습니다.") }
    }
}
