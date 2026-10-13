package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/** 퇴장 스캔 요청(#120). 입장한 QR과 같은 값을 보낸다. 아래 셋 중 하나만 보낸다. */
@Schema(
    description =
        "퇴장 스캔 요청. 일반 참가자는 `participantId`와 `code`, 연수자는 `traineeId`, 현장 종이 QR은 `token` 중 하나만 보낸다.",
)
data class ScanExitReqDto(
    @field:Schema(description = "QR의 참가자 ID. `code`와 함께 보낸다(둘 중 하나만 보내면 400)", example = "55", nullable = true)
    @field:Positive
    val participantId: Long? = null,
    @field:Schema(description = "QR의 참가자 코드(1~22자). `participantId`와 함께 보낸다", example = "sample-code-not-real", nullable = true)
    @field:Size(min = 1, max = 22)
    val code: String? = null,
    @field:Schema(description = "QR의 연수자 ID", example = "701", nullable = true)
    @field:Positive
    val traineeId: Long? = null,
    @field:Schema(description = "종이 QR 토큰(최대 64자)", example = "sample-token-not-real", nullable = true)
    @field:Size(min = 1, max = 64)
    val token: String? = null,
) {
    // 코드와 토큰이 로그에 남지 않게 한다
    override fun toString() = "ScanExitReqDto(participantId=$participantId, traineeId=$traineeId)"
}
