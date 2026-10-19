package team.startup.expo.global.client.user

/** 유저 서비스 `POST /internal/participants/resolve` 요청. `participationType`은 `STANDARD`, `TRAINEE`다. */
data class ResolveParticipantReqDto(
    val expoId: String,
    val phoneNumber: String,
    val participationType: String,
) {
    override fun toString() = "ResolveParticipantReqDto(expoId=$expoId, participationType=$participationType)"
}

data class ResolveParticipantResDto(
    val participantId: Long,
    val participationType: String,
)
