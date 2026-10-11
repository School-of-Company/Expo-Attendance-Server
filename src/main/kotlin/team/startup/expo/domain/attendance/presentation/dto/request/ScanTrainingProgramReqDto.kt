package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 연수 프로그램 스캔. 이전 방식은 `traineeId`이고, 사전등록 참가자의 QR은 `participantId`와 `code`로 보낸다(연결된 연수자를
 * 유저 서비스가 찾는다). 둘 다 오면 참가자 ID와 코드가 우선한다.
 */
data class ScanTrainingProgramReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Positive
    val traineeId: Long? = null,
    @field:Positive
    val participantId: Long? = null,
    @field:Size(max = 64)
    val code: String? = null,
) {
    // 참가자 코드는 QR에 담기는 값이라 로그에 남지 않게 한다
    override fun toString() = "ScanTrainingProgramReqDto(expoId=$expoId, traineeId=$traineeId, participantId=$participantId)"
}
