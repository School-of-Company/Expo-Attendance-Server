package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.LocalDate
import java.util.UUID

enum class FieldVisitStatus { ACTIVE, CANCELLED }

@Schema(description = "날짜별 현장등록 projection 요청")
data class ProjectFieldVisitReqDto(
    @field:Schema(description = "KST 방문일")
    val visitDate: LocalDate,
    @field:Positive
    @field:Schema(description = "등록 세대", minimum = "1")
    val generation: Long,
    @field:Schema(description = "등록 상태")
    val status: FieldVisitStatus,
    @field:Schema(description = "멱등 작업 ID")
    val operationId: UUID,
)

@Schema(description = "방문일 취소 상태 조회·취소 보호 획득 요청")
data class FieldVisitCancellationReqDto(
    @field:NotBlank
    @field:Schema(description = "소유 확인용 전화번호")
    val phoneNumber: String,
    @field:Schema(description = "KST 취소 대상 방문일")
    val visitDate: LocalDate,
    @field:Positive
    @field:Schema(description = "등록 세대", minimum = "1")
    val generation: Long,
) {
    override fun toString() = "FieldVisitCancellationReqDto(visitDate=$visitDate, generation=$generation)"
}

@Schema(description = "방문일 취소 보호 확정·중단 요청")
data class FinishFieldVisitGuardReqDto(
    @field:Schema(description = "KST 취소 대상 방문일")
    val visitDate: LocalDate,
    @field:Positive
    @field:Schema(description = "등록 세대", minimum = "1")
    val generation: Long,
)
