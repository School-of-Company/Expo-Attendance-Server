package team.startup.expo.domain.attendance.support

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate

/** 집계할 기간. `all`이면 박람회 전체, 아니면 `date`(없으면 오늘) 하루다. */
data class EntryScope(
    val from: LocalDate,
    val to: LocalDate,
    /** 하루를 집계할 때만 그 날짜이고, 전체 기간이면 비어 있다. */
    val date: LocalDate?,
) {
    companion object {
        private val EARLIEST = LocalDate.of(2000, 1, 1)
        private val LATEST = LocalDate.of(2999, 12, 31)

        fun of(
            date: LocalDate?,
            all: Boolean,
            clock: Clock,
        ): EntryScope {
            if (all) {
                if (date != null) throw ExpectedException(HttpStatus.BAD_REQUEST, "date와 all을 함께 보낼 수 없습니다.")
                return EntryScope(EARLIEST, LATEST, null)
            }
            val day = date ?: LocalDate.now(clock)
            return EntryScope(day, day, day)
        }
    }
}
