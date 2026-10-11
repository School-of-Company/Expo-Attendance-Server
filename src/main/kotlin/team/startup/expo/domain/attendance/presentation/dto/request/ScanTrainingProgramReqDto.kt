package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 연수 프로그램 스캔. 이전 방식은 `traineeId`이고, 사전등록 참가자의 QR은 `participantId`와 `code`로 보낸다(연결된 연수자를
 * 유저 서비스가 찾는다). 둘 다 오면 참가자 ID와 코드가 우선한다.
 */
@Schema(
    description =
        "연수 프로그램 출석 스캔 요청. `traineeId`(이전 방식) 또는 `participantId`와 `code`(사전등록 QR) 중 하나를 보낸다. " +
            "둘 다 보내면 참가자 ID와 코드가 우선하고, `participantId`와 `code` 중 하나만 보내면 400이다.",
)
data class ScanTrainingProgramReqDto(
    @field:Schema(description = "박람회 ID(UUID 문자열, 최대 36자)", example = "00000000-0000-0000-0000-000000000000")
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Schema(description = "연수자 ID(이전 방식)", example = "31", nullable = true)
    @field:Positive
    val traineeId: Long? = null,
    @field:Schema(description = "사전등록 QR의 참가자 ID. `code`와 함께 보낸다", example = "55", nullable = true)
    @field:Positive
    val participantId: Long? = null,
    // 유저 서비스의 참가자 code(22자)와 맞춘다. 더 길면 유저 서비스가 400으로 거절하고 우리는 그것을 503으로 바꾸게 된다.
    @field:Schema(description = "사전등록 QR의 참가자 코드(최대 22자). `participantId`와 함께 보낸다", example = "sample-code-not-real", nullable = true)
    @field:Size(max = 22)
    val code: String? = null,
) {
    // 참가자 코드는 QR에 담기는 값이라 로그에 남지 않게 한다
    override fun toString() = "ScanTrainingProgramReqDto(expoId=$expoId, traineeId=$traineeId, participantId=$participantId)"
}
