package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ScanStandardProgramReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Positive
    val participantId: Long,
    // 기존 앱이 함께 보내지만 참가자는 `participantId`로 찾으므로 쓰지 않는다. 로그에 남지 않게 한다.
    val phoneNumber: String? = null,
    // 입장 스캔과 같은 참가자 code. 있으면 유저 서비스에서 맞는지 확인한다(없을 때의 처리는 설정으로 정한다).
    // 유저 서비스의 참가자 code(22자)와 맞춘다. 더 길면 유저 서비스가 400으로 거절하고 우리는 그것을 503으로 바꾸게 된다.
    @field:Size(max = 22)
    val code: String? = null,
) {
    override fun toString() = "ScanStandardProgramReqDto(expoId=$expoId, participantId=$participantId)"
}
