package team.startup.expo.global.client.user

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 유저 서비스 `POST /internal/entries` 요청. `participationType`은 `STANDARD`, `TRAINEE`다. 일반 참가자는
 * `participantId`와 `code`로도 찾으며(동행자는 번호가 없다) 이 값이 있으면 그 경로가 우선이다. 연수자는 번호가 필요하다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordEntryReqDto(
    val expoId: String,
    val participationType: String,
    val phoneNumber: String? = null,
    val participantId: Long? = null,
    val code: String? = null,
)

/** 연수자는 `occupation`이 없고(연수자는 모두 교사) `school`만 있다. 동행자는 `phoneNumber`가 없다. */
data class RecordEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val participationType: String,
    val occupation: String?,
    val school: String?,
)
