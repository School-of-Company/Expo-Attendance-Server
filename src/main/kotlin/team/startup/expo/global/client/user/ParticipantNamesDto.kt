package team.startup.expo.global.client.user

/** 유저 서비스 `POST /internal/standard-participants/names` 요청. 없는 id나 다른 박람회의 참가자가 있으면 404다. */
data class StandardParticipantNamesReqDto(
    val expoId: String,
    val participantIds: List<Long>,
)

data class StandardParticipantNameResDto(
    val participantId: Long,
    val name: String,
)

/** 유저 서비스 `POST /internal/trainees/names` 요청. 없는 id나 다른 박람회의 연수자가 있으면 404다. */
data class TraineeNamesReqDto(
    val expoId: String,
    val traineeIds: List<Long>,
)

data class TraineeNameResDto(
    val traineeId: Long,
    val name: String,
)
