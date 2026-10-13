package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class EntrySummaryReqDto(
    @field:Schema(description = "집계할 날짜(`yyyy-MM-dd`). 없으면 오늘(Asia/Seoul)", example = "2026-10-31", nullable = true)
    val date: LocalDate? = null,
)
