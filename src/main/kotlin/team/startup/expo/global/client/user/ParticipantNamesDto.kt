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

/** 유저 서비스 `POST /internal/standard-participants/details` 요청. 없는 id나 다른 박람회의 참가자가 있으면 404다. */
data class StandardParticipantBriefsReqDto(
    val expoId: String,
    val participantIds: List<Long>,
)

/**
 * 참가자 요약. 동행자는 `phoneNumber`가 없다. `notificationPhoneNumber`는 문자를 받을 번호(본인 번호, 없으면 대표자 번호)이며
 * 유저 서비스가 이 필드를 내려주기 전에는 `null`이다.
 */
data class StandardParticipantBriefResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val notificationPhoneNumber: String? = null,
)
