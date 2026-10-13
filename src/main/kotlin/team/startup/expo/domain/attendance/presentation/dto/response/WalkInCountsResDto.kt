package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.WalkInCategory
import java.time.LocalDate

@Schema(description = "간편입장 구분별 합계")
data class WalkInCountsResDto(
    val expoId: String,
    val date: LocalDate,
    @field:Schema(description = "모든 구분의 합계(0인 구분도 포함한다)")
    val counts: List<WalkInCountResDto>,
    @field:Schema(description = "전체 합계", example = "54")
    val total: Long,
)

data class WalkInCountResDto(
    val category: WalkInCategory,
    @field:Schema(description = "이 구분의 오늘까지 합계(취소 반영)", example = "48")
    val count: Long,
)
