package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

@Schema(description = "스캐너별 입장 처리 건수 조회 요청")
data class ScannerEntryCountsReqDto(
    @field:Schema(description = "집계할 날짜(`yyyy-MM-dd`). 없으면 오늘(Asia/Seoul). `all`과 함께 보내면 400", example = "2026-10-31", nullable = true)
    val date: LocalDate? = null,
    @field:Schema(description = "`true`면 박람회 전체 기간을 집계한다. `date`와 함께 보내면 400", defaultValue = "false")
    val all: Boolean = false,
    @field:Schema(description = "상위 몇 개 스캐너까지(1~100). 없으면 15", example = "15", minimum = "1", maximum = "100", defaultValue = "15")
    @field:Min(1)
    @field:Max(MAX_LIMIT.toLong())
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 15
        const val MAX_LIMIT = 100
    }
}
