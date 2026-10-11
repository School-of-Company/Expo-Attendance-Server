package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "박람회 입장 스캔 결과")
data class ScanEntryResDto(
    @field:Schema(description = "참가자(연수자) ID", example = "55")
    val id: Long,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    /** 번호가 없는 참가자(동행자)는 `null`이다. */
    @field:Schema(description = "전화번호. 번호가 없는 참가자(동행자)는 `null`이다", example = "010-0000-0000", nullable = true)
    val phoneNumber: String?,
    @field:Schema(description = "개인정보 수집 동의 여부", example = "true")
    val personalInformationStatus: Boolean,
    @field:Schema(description = "참가 유형(유저 서비스의 `participationType`)", example = "STANDARD")
    val participationType: String,
    /** 명찰 출력 대상(연수자 전원, 교사인 일반 참가자)이 아니면 `null`이다. */
    @field:Schema(description = "명찰 정보. 명찰 출력 대상(연수자 전원, 교사인 일반 참가자)이 아니면 `null`이다", nullable = true)
    val badge: BadgeResDto?,
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
