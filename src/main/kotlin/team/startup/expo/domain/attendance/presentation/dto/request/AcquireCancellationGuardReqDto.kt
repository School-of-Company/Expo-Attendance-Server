package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AcquireCancellationGuardReqDto(
    /** 유저 서비스가 소유 확인에 쓰는 검증된 전화번호. 로그에 남지 않도록 `toString`에서 뺀다. */
    @field:NotBlank
    @field:Size(max = 20)
    val phoneNumber: String,
) {
    override fun toString() = "AcquireCancellationGuardReqDto()"
}
