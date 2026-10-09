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
    @field:Size(max = 64)
    val code: String? = null,
) {
    override fun toString() = "ScanStandardProgramReqDto(expoId=$expoId, participantId=$participantId)"
}
