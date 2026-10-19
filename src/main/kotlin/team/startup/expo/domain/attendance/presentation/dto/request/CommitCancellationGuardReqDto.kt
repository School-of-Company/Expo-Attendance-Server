package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(description = "취소 보호 확정 요청. 본문 없이 보내도 된다(기존 방식).")
data class CommitCancellationGuardReqDto(
    @field:Schema(
        description = "이 취소의 사전등록 회차 DELETE에 붙인 revision. 보내면 더 오래된 회차 PUT이 확정된 보호를 풀지 못하고, 이미 더 새로운 PUT이 적용되었으면 보호를 확정하지 않는다.",
        example = "4",
        nullable = true,
    )
    @field:Positive
    val revision: Long? = null,
    @field:Schema(
        description =
            "이 취소가 회차 하나만 취소하면 그 회차 ID(#160). 보내면 그 회차를 바로 취소로 표시하고, 확정 상태인 다른 회차가 남아 있으면 " +
                "보호를 확정하지 않고 끝내 남은 회차로 입장할 수 있게 한다. 없으면 참가자의 모든 신청을 취소한 것으로 본다(기존 방식).",
        example = "12",
        nullable = true,
    )
    @field:Positive
    val sessionId: Long? = null,
)
