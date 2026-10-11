package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.CancellationGuardState

@Schema(description = "참가자 기준 취소 상태. 조회한 시점의 값이며 최종 취소는 취소 보호 acquire로 다시 확인한다.")
data class CancellationStatusResDto(
    @field:Schema(description = "참가자 ID", example = "7001")
    val participantId: Long,
    @field:Schema(description = "요청한 번호가 이 참가자의 대표자 번호로 확인되었는지(유저 서비스 판단)")
    val representative: Boolean,
    @field:Schema(description = "박람회 전체(날짜 무관) 입장 기록이 있는지(유저 서비스 기록)")
    val entered: Boolean,
    @field:Schema(description = "일반 프로그램 또는 연결된 연수자의 연수 프로그램에 출석한 기록이 하나라도 있는지")
    val programAttended: Boolean,
    @field:Schema(description = "연결된 연수자가 있는지")
    val traineeLinked: Boolean,
    @field:Schema(description = "연결된 연수자 ID. 연결이 없으면 null", nullable = true)
    val linkedTraineeId: Long?,
    @field:Schema(description = "이 서비스의 취소 보호 상태. 없으면 null, 진행 중이면 HELD, 확정되었으면 COMMITTED", nullable = true)
    val guardState: CancellationGuardState?,
)
