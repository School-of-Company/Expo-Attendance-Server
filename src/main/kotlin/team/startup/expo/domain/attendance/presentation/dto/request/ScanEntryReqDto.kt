package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ScanEntryReqDto(
    val authority: EntryAuthority,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
) {
    // 전화번호가 로그에 남지 않게 한다
    override fun toString() = "ScanEntryReqDto(authority=$authority, phoneNumber=***)"
}
