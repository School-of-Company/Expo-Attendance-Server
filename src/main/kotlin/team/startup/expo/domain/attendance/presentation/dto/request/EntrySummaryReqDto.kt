package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class EntrySummaryReqDto(
    @field:Schema(description = "집계할 날짜(`yyyy-MM-dd`). 없으면 오늘(Asia/Seoul). `all`과 함께 보내면 400", example = "2026-10-31", nullable = true)
    val date: LocalDate? = null,
    @field:Schema(description = "`true`면 날짜와 상관없이 박람회 전체 기간을 집계한다(\"전체 기록\"). `date`와 함께 보내면 400", defaultValue = "false")
    val all: Boolean = false,
)
