package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 일반 참가자는 QR의 `participantId`와 `code`로 입장한다(동행자는 전화번호가 없다). 이전 QR을 위해 전화번호도
 * 받는다. 연수자는 전화번호가 필요하다. 어떤 값이 필요한지는 서비스가 `authority`와 함께 확인한다.
 */
@Schema(
    description =
        "박람회 입장 스캔 요청. 일반 참가자(`ROLE_STANDARD`)는 `participantId`와 `code`를 함께 보내거나(이전 QR은 `phoneNumber`), " +
            "연수자(`ROLE_TRAINEE`)는 `phoneNumber`를 보낸다.",
)
data class ScanEntryReqDto(
    @field:Schema(description = "입장 대상", example = "ROLE_STANDARD")
    val authority: EntryAuthority,
    @field:Schema(
        description = "전화번호(최대 30자). 연수자는 필수, 일반 참가자는 이전 QR일 때만 사용한다",
        example = "010-0000-0000",
        nullable = true,
    )
    @field:Size(max = 30)
    val phoneNumber: String? = null,
    @field:Schema(description = "QR의 참가자 ID. `code`와 함께 보낸다(둘 중 하나만 보내면 400)", example = "55", nullable = true)
    @field:Positive
    val participantId: Long? = null,
    @field:Schema(description = "QR의 참가자 코드(1~22자). `participantId`와 함께 보낸다", example = "sample-code-not-real", nullable = true)
    @field:Size(min = 1, max = 22)
    val code: String? = null,
) {
    // 전화번호와 코드가 로그에 남지 않게 한다
    override fun toString() = "ScanEntryReqDto(authority=$authority, participantId=$participantId)"
}
