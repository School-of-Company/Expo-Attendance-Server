package team.startup.expo.domain.attendance.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import team.startup.expo.support.shouldFailWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

class EntryRecordCursorSpec :
    BehaviorSpec({
        given("입장 기록 커서(#250)") {
            `when`("인코딩한 값을 다시 읽으면") {
                val cursor = EntryRecordCursor(LocalDate.of(2026, 10, 30), LocalDateTime.of(2026, 10, 30, 23, 59, 59, 123_000_000), 42L)

                then("같은 날짜·시각·id다") {
                    EntryRecordCursor.decode(cursor.encode()) shouldBe cursor
                }
                then("URL에 그대로 넣을 수 있는 문자만 쓴다") {
                    Regex("[A-Za-z0-9_-]+").matches(cursor.encode()) shouldBe true
                }
            }

            `when`("기간 시작일로 처음 위치를 만들면") {
                val start = EntryRecordCursor.before(LocalDate.of(2026, 10, 30))

                then("그날 0시의 첫 이벤트보다도 앞이다") {
                    start shouldBe EntryRecordCursor(LocalDate.of(2026, 10, 29), LocalDateTime.of(2026, 10, 29, 0, 0), 0L)
                }
            }

            listOf(
                "base64가 아님" to "!!!",
                "칸 수가 다름" to Base64.getUrlEncoder().encodeToString("2026-10-30|1".toByteArray()),
                "시각 형식이 틀림" to Base64.getUrlEncoder().encodeToString("2026-10-30|x|1".toByteArray()),
                "id가 숫자가 아님" to Base64.getUrlEncoder().encodeToString("2026-10-30|2026-10-30T10:00|a".toByteArray()),
            ).forEach { (name, value) ->
                `when`("$name 이면") {
                    then("400이다") {
                        shouldFailWith(HttpStatus.BAD_REQUEST, "cursor가 올바르지 않습니다.") { EntryRecordCursor.decode(value) }
                    }
                }
            }
        }
    })
