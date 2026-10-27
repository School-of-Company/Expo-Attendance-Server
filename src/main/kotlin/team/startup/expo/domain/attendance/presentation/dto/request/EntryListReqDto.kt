package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

@Schema(description = "하루 입장자 목록 조회 요청")
data class EntryListReqDto(
    @field:Schema(description = "조회할 날짜(`yyyy-MM-dd`). 없으면 오늘(Asia/Seoul)", example = "2026-10-31", nullable = true)
    val date: LocalDate? = null,
    @field:Schema(description = "0부터 시작하는 페이지 번호", example = "0", minimum = "0", defaultValue = "0")
    @field:Min(0)
    val page: Int = 0,
    @field:Schema(description = "한 페이지의 행 수(1~500). 없으면 500", example = "500", minimum = "1", maximum = "500", defaultValue = "500")
    @field:Min(1)
    @field:Max(MAX_SIZE.toLong())
    val size: Int = MAX_SIZE,
) {
    companion object {
        const val MAX_SIZE = 500
    }
}
