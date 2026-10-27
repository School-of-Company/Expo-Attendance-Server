package team.startup.expo.global.client.user

/**
 * 유저 서비스 `POST /internal/participants/entry-cards` 요청(Expo-User-Server#215). 목록마다 최대 1,000개이고 아무것도 기록하지 않는다.
 * 없는 id와 다른 박람회의 id는 404 없이 결과에서 빠진다.
 */
data class EntryCardsReqDto(
    val expoId: String,
    val standardParticipantIds: List<Long>,
    val traineeIds: List<Long>,
)

data class EntryCardsResDto(
    val standardParticipants: List<StandardParticipantEntryCardResDto>,
    val trainees: List<TraineeEntryCardResDto>,
)

/** 동행자는 `phoneNumber`가 없다. 전화번호는 로그에 남지 않도록 `toString`에서 뺀다. */
data class StandardParticipantEntryCardResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val occupation: String?,
    val school: String?,
) {
    override fun toString() = "StandardParticipantEntryCardResDto(participantId=$participantId)"
}

/** 연수자는 `occupation`이 없다(연수자는 모두 교사). 전화번호는 로그에 남지 않도록 `toString`에서 뺀다. */
data class TraineeEntryCardResDto(
    val traineeId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val school: String?,
) {
    override fun toString() = "TraineeEntryCardResDto(traineeId=$traineeId)"
}
