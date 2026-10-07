package team.startup.expo.global.client.user

/** 유저 서비스 `POST /internal/entries` 요청. `participationType`은 `STANDARD`, `TRAINEE`다. */
data class RecordEntryReqDto(
    val expoId: String,
    val participationType: String,
    val phoneNumber: String,
)

/** 연수자는 `occupation`이 없고(연수자는 모두 교사) `school`만 있다. */
data class RecordEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val personalInformationStatus: Boolean,
    val participationType: String,
    val occupation: String?,
    val school: String?,
)

/** 유저 서비스 `POST /internal/participants/resolve` 요청. */
data class ResolveParticipantReqDto(
    val expoId: String,
    val phoneNumber: String,
    val participationType: String,
)

data class ResolveParticipantResDto(
    val participantId: Long,
    val participationType: String,
)
