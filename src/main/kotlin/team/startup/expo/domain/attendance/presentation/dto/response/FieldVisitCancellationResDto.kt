package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

enum class FieldVisitGuardState { NONE, HELD, COMMITTED, ABORTED }

data class FieldVisitCancellationResDto(
    @field:Schema(description = "등록 ID")
    val registrationId: UUID,
    @field:Schema(description = "참가자 ID")
    val participantId: Long,
    @field:Schema(description = "KST 방문일")
    val visitDate: LocalDate,
    @field:Schema(description = "등록 세대")
    val generation: Long,
    @field:Schema(description = "대상 방문일 최초 입장 여부; 퇴장해도 true")
    val enteredOnVisitDate: Boolean,
    @field:Schema(description = "대상 날짜 프로그램 출석 여부")
    val programAttended: Boolean,
    @field:Schema(description = "보호 상태; 중단된 보호는 NONE", allowableValues = ["NONE", "HELD", "COMMITTED"])
    val guardState: FieldVisitGuardState,
    @field:Schema(description = "연결된 연수자 ID", nullable = true)
    val linkedTraineeId: Long?,
)
