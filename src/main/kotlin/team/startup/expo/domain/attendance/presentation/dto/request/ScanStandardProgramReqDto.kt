package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "일반 프로그램 출석 스캔 요청")
data class ScanStandardProgramReqDto(
    @field:Schema(description = "박람회 ID(UUID 문자열, 최대 36자)", example = "00000000-0000-0000-0000-000000000000", minLength = 1)
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Schema(description = "참가자 ID", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:Positive
    val participantId: Long,
    // 기존 앱이 함께 보내지만 참가자는 `participantId`로 찾으므로 쓰지 않는다. 로그에 남지 않게 한다.
    @field:Schema(
        description = "기존 앱이 함께 보내는 전화번호. 참가자는 `participantId`로 찾으므로 쓰지 않는다",
        example = "010-0000-0000",
        nullable = true,
    )
    val phoneNumber: String? = null,
    // 입장 스캔과 같은 참가자 code. 있으면 유저 서비스에서 맞는지 확인한다(없을 때의 처리는 설정으로 정한다).
    // 유저 서비스의 참가자 code(22자)와 맞춘다. 더 길면 유저 서비스가 400으로 거절하고 우리는 그것을 503으로 바꾸게 된다.
    @field:Schema(
        description =
            "입장 스캔과 같은 참가자 코드(최대 22자). 보내면 맞는지 확인하고(틀리면 참가자를 찾지 못한 것과 같은 404), " +
                "없으면 `program-attendance.require-code` 설정이 꺼져 있을 때만 참가자 ID만으로 받아 준다",
        example = "sample-code-not-real",
        nullable = true,
    )
    @field:Size(max = 22)
    val code: String? = null,
) {
    override fun toString() = "ScanStandardProgramReqDto(expoId=$expoId, participantId=$participantId)"
}
