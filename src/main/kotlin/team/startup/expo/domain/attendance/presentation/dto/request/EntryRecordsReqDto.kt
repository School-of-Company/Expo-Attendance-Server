package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

@Schema(description = "입장 기록 목록 조회 요청")
data class EntryRecordsReqDto(
    @field:Schema(
        description = "입장 날짜 범위의 시작(`yyyy-MM-dd`, 포함). `to`와 함께 보내거나 둘 다 생략한다(생략하면 박람회 전체). 하나만 보내면 400",
        example = "2026-10-19",
        nullable = true,
    )
    val from: LocalDate? = null,
    @field:Schema(description = "입장 날짜 범위의 끝(`yyyy-MM-dd`, 포함). `from`보다 빠르면 400", example = "2026-10-19", nullable = true)
    val to: LocalDate? = null,
    @field:Schema(description = "페이지 번호(0부터). 없으면 0", example = "0", minimum = "0", defaultValue = "0")
    @field:Min(0)
    val page: Int = 0,
    @field:Schema(description = "페이지 크기(1~5000). 없으면 1000", example = "1000", minimum = "1", maximum = "5000", defaultValue = "1000")
    @field:Min(1)
    @field:Max(MAX_SIZE.toLong())
    val size: Int = DEFAULT_SIZE,
) {
    companion object {
        const val DEFAULT_SIZE = 1000
        const val MAX_SIZE = 5000
    }
}
