package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.EntryStatus

@Schema(description = "박람회 입장 스캔 결과")
data class ScanEntryResDto(
    @field:Schema(description = "참가자(연수자) ID", example = "55")
    val id: Long,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    /** 번호가 없는 참가자(동행자)는 `null`이다. */
    @field:Schema(
        description = "전화번호. 번호가 없는 참가자(동행자)는 `null`이다. 재입장일 때는 전화번호로 스캔한 경우에만 스캔한 번호가 담긴다",
        example = "010-0000-0000",
        nullable = true,
    )
    val phoneNumber: String?,
    @field:Schema(description = "개인정보 수집 동의 여부. 재입장일 때는 없을 수 있다", example = "true", nullable = true)
    val personalInformationStatus: Boolean?,
    @field:Schema(description = "참가 유형(유저 서비스의 `participationType`)", example = "STANDARD")
    val participationType: String,
    /** 명찰 출력 대상(연수자 전원, 교사인 일반 참가자)이 아니면 `null`이다. */
    @field:Schema(
        description = "명찰 정보. 명찰 출력 대상(연수자 전원, 교사인 일반 참가자)이 아니거나 재입장이면 `null`이다",
        nullable = true,
    )
    val badge: BadgeResDto?,
    @field:Schema(description = "입장 상태. 오늘 처음이면 `FIRST`, 퇴장한 뒤 다시 입장하면 `RE_ENTRY`", example = "FIRST")
    val entryStatus: EntryStatus,
    @field:Schema(description = "오늘 최초 입장 시각(`HH:mm`). 재입장이어도 처음 입장한 시각이다", example = "10:05", nullable = true)
    val firstEnteredTime: String?,
) {
    override fun toString() = "ScanEntryResDto(id=$id, participationType=$participationType)"
}

@Schema(description = "명찰 출력 정보")
data class BadgeResDto(
    @field:Schema(description = "명찰에 찍을 이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "소속 학교(없으면 `null`)", example = "예시고등학교", nullable = true)
    val school: String?,
    /** 입구 스캔 QR과 같은 값의 JSON 문자열이다(참가자 ID와 코드, 이전 방식은 전화번호). 이름·소속은 담지 않는다. */
    @field:Schema(
        description = "입구 스캔 QR과 같은 값의 JSON 문자열(참가자 ID와 코드, 이전 방식은 전화번호). 이름·소속은 담지 않는다",
        example = "{\"participantId\":55,\"code\":\"sample-code-not-real\"}",
    )
    val qrCode: String,
)
