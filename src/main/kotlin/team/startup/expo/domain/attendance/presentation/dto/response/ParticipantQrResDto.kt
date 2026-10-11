package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/** 참가자 QR 값(`{"participantId","code"}`)의 재료. `code`는 로그에 남지 않도록 `toString`에서 뺀다. */
@Schema(description = "참가자 QR 값의 재료. 이 두 값으로 QR(`{\"participantId\",\"code\"}`)을 만든다")
data class ParticipantQrResDto(
    @field:Schema(description = "참가자 ID", example = "55")
    val participantId: Long,
    @field:Schema(description = "참가자 코드(입장 인증 값이라 로그에 남기지 않는다)", example = "sample-code-not-real")
    val code: String,
) {
    override fun toString() = "ParticipantQrResDto(participantId=$participantId)"
}
