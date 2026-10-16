package team.startup.expo.global.client.user

/** User의 기존 읽기 전용 조회. 입장을 기록하지 않으며 전화번호는 로그에 남기지 않는다. */
data class ResolveStandardParticipantReqDto(
    val expoId: String,
    val phoneNumber: String,
) {
    override fun toString() = "ResolveStandardParticipantReqDto(expoId=$expoId)"
}

data class ResolveStandardParticipantResDto(
    val participantId: Long,
)
