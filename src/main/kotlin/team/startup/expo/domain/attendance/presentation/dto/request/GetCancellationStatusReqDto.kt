package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "참가자 취소 상태 조회 요청")
data class GetCancellationStatusReqDto(
    @field:Schema(description = "유저 서비스가 소유 확인에 쓰는 검증된 전화번호. URL이 아니라 본문으로만 받고 로그에 남기지 않는다.", example = "010-0000-0000")
    @field:NotBlank
    @field:Size(max = 20)
    val phoneNumber: String,
) {
    override fun toString() = "GetCancellationStatusReqDto()"
}
