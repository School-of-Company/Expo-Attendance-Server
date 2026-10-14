package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

@Schema(description = "최근 입·퇴장 기록 조회 요청")
data class RecentEntryRecordsReqDto(
    @field:Schema(description = "조회할 날짜(`yyyy-MM-dd`). 없으면 오늘(Asia/Seoul). `all`과 함께 보내면 400", example = "2026-10-31", nullable = true)
    val date: LocalDate? = null,
    @field:Schema(description = "`true`면 박람회 전체 기간에서 최신 기록을 가져온다. `date`와 함께 보내면 400", defaultValue = "false")
    val all: Boolean = false,
    @field:Schema(description = "가져올 건수(1~100). 없으면 30", example = "30", minimum = "1", maximum = "100", defaultValue = "30")
    @field:Min(1)
    @field:Max(MAX_LIMIT.toLong())
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 30
        const val MAX_LIMIT = 100
    }
}
