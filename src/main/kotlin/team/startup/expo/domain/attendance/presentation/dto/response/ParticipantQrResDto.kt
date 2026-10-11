package team.startup.expo.domain.attendance.presentation.dto.response

/** 참가자 QR 값(`{"participantId","code"}`)의 재료. `code`는 로그에 남지 않도록 `toString`에서 뺀다. */
data class ParticipantQrResDto(
    val participantId: Long,
    val code: String,
) {
    override fun toString() = "ParticipantQrResDto(participantId=$participantId)"
}
