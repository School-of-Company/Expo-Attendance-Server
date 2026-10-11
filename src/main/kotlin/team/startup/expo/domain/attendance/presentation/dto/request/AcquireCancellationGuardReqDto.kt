package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "신청 취소 보호 요청")
data class AcquireCancellationGuardReqDto(
    /** 유저 서비스가 소유 확인에 쓰는 검증된 전화번호. 로그에 남지 않도록 `toString`에서 뺀다. */
    @field:Schema(description = "유저 서비스가 소유 확인에 쓰는 검증된 전화번호(최대 20자)", example = "010-0000-0000")
    @field:NotBlank
    @field:Size(max = 20)
    val phoneNumber: String,
) {
    override fun toString() = "AcquireCancellationGuardReqDto()"
}
