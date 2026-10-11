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

/** 유저 서비스 `POST /internal/standard-participants/verify` 요청. 입장·출석을 기록하지 않고 ID와 code가 맞는지만 확인한다. */
data class VerifyStandardParticipantReqDto(
    val expoId: String,
    val participantId: Long,
    val code: String,
)

/**
 * 유저 서비스 `POST /internal/standard-participants/trainee` 요청(제안, Expo-User-Server#79). 참가자 ID와 code가 맞으면 그 참가자에
 * 연결된 연수자를 돌려준다. 입장·출석을 기록하지 않는다.
 */
data class ResolveTraineeByParticipantReqDto(
    val expoId: String,
    val participantId: Long,
    val code: String,
)

data class ResolveTraineeByParticipantResDto(
    val traineeId: Long,
)
