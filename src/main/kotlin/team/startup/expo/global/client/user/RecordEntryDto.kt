package team.startup.expo.global.client.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.util.UUID

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
    /** 입장 시간을 확인한 사전등록 회차. 일반 참가자 QR 입장에만 보내고, 없으면 생략해 회차 없이 기록된다(#142). */
    val preregisterSessionId: Long? = null,
    val attendanceDate: LocalDate? = null,
    val fieldVisitRegistrationId: UUID? = null,
    val fieldVisitGeneration: Long? = null,
)

data class ParticipantEntryStatusReqDto(
    val expoId: String,
    val participantId: Long,
    val attendanceDate: LocalDate,
)

data class ParticipantEntryStatusResDto(
    val participantId: Long,
    val attendanceDate: LocalDate,
    val entered: Boolean,
)

/**
 * 연수자는 `occupation`이 없고(연수자는 모두 교사) `school`만 있다. 동행자는 `phoneNumber`가 없다.
 * `badgePrint`는 유저 서비스가 입장과 같은 트랜잭션에 저장한 명찰 자동 출력 작업 결과다(`QUEUED`, `DUPLICATE`, 대상 아님은 `null`).
 * `badgePrintJobId`는 그 작업의 ID다(`DUPLICATE`면 같은 날 이미 있던 작업). 출력대 조회에 쓴다(#279).
 * 이 필드들이 없는 이전 유저 서비스 응답은 `null`로 읽는다(#275).
 */
data class RecordEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val participationType: String,
    val occupation: String?,
    val school: String?,
    val badgePrint: String? = null,
    val badgePrintJobId: Long? = null,
)
