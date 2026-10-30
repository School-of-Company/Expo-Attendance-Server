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
    /** 유저 서비스가 준 값을 그대로 옮긴다. 재입장, 재생처럼 유저 서비스가 결과를 주지 않으면 `null`이다(#275). */
    @field:Schema(
        description =
            "명찰 자동 출력 요청 결과. `QUEUED`는 출력 작업 저장, `DUPLICATE`는 오늘 작업이 이미 있음, " +
                "`null`은 대상이 아니거나 결과를 알 수 없음(재입장, 재생, 이전 유저 서비스). 실제 출력 완료를 뜻하지 않는다",
        allowableValues = ["QUEUED", "DUPLICATE"],
        example = "QUEUED",
        nullable = true,
    )
    val badgePrint: String? = null,
) {
    override fun toString() = "ScanEntryResDto(id=$id, participationType=$participationType)"
}

@Schema(description = "명찰 출력 정보")
data class BadgeResDto(
    @field:Schema(description = "명찰에 찍을 이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "소속 학교(없으면 `null`)", example = "예시고등학교", nullable = true)
    val school: String?,
)
