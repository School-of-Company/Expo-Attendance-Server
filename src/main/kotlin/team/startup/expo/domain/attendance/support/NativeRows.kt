package team.startup.expo.domain.attendance.support

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 네이티브 쿼리 결과(`Array<Any?>`)의 열 변환. 드라이버와 Hibernate 설정에 따라 날짜·시각 열이 `java.sql` 타입으로도,
 * `java.time` 타입으로도 올 수 있어 둘 다 받는다. 그 밖의 타입은 쿼리와 맞지 않는 것이라 [IllegalStateException]이다.
 */
object NativeRows {
    fun date(value: Any?): LocalDate =
        when (value) {
            is LocalDate -> value
            is Date -> value.toLocalDate()
            else -> throw unknownType("날짜", value)
        }

    fun dateTime(value: Any?): LocalDateTime =
        when (value) {
            is LocalDateTime -> value
            is Timestamp -> value.toLocalDateTime()
            else -> throw unknownType("시각", value)
        }

    /** 비어 있을 수 있는 시각 열. */
    fun dateTimeOrNull(value: Any?): LocalDateTime? = value?.let { dateTime(it) }

    /** 집계 열(`COUNT`, `SUM`). 드라이버에 따라 `Long`, `BigInteger`, `BigDecimal`로 온다. */
    fun long(value: Any?): Long = (value as Number).toLong()

    private fun unknownType(
        kind: String,
        value: Any?,
    ) = IllegalStateException("$kind 열의 타입을 알 수 없습니다: ${value?.javaClass}")
}
