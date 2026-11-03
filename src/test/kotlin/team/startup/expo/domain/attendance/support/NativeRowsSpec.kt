package team.startup.expo.domain.attendance.support

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

class NativeRowsSpec :
    BehaviorSpec({
        val day = LocalDate.of(2026, 10, 15)
        val at = LocalDateTime.of(2026, 10, 15, 9, 30, 15)

        given("날짜 열") {
            then("LocalDate와 java.sql.Date를 모두 읽는다") {
                NativeRows.date(day) shouldBe day
                NativeRows.date(Date.valueOf(day)) shouldBe day
            }
            then("다른 타입이나 null이면 IllegalStateException이다") {
                shouldThrow<IllegalStateException> { NativeRows.date("2026-10-15") }
                shouldThrow<IllegalStateException> { NativeRows.date(null) }
            }
        }

        given("시각 열") {
            then("LocalDateTime과 Timestamp를 모두 읽는다") {
                NativeRows.dateTime(at) shouldBe at
                NativeRows.dateTime(Timestamp.valueOf(at)) shouldBe at
                NativeRows.dateTimeOrNull(Timestamp.valueOf(at)) shouldBe at
            }
            then("비어 있을 수 있는 열은 null을 그대로 돌려준다") {
                NativeRows.dateTimeOrNull(null) shouldBe null
            }
            then("다른 타입이거나 필수 열이 null이면 IllegalStateException이다") {
                shouldThrow<IllegalStateException> { NativeRows.dateTime(day) }
                shouldThrow<IllegalStateException> { NativeRows.dateTime(null) }
                shouldThrow<IllegalStateException> { NativeRows.dateTimeOrNull(day) }
            }
        }

        given("집계 열") {
            then("숫자 타입과 상관없이 Long으로 읽는다") {
                NativeRows.long(3L) shouldBe 3L
                NativeRows.long(3) shouldBe 3L
                NativeRows.long(BigInteger.valueOf(3)) shouldBe 3L
                NativeRows.long(BigDecimal("3")) shouldBe 3L
            }
        }
    })
