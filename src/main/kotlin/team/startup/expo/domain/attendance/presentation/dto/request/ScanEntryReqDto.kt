package team.startup.expo.domain.attendance.presentation.dto.request

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 일반 참가자는 QR의 `participantId`와 `code`로 입장한다(동행자는 전화번호가 없다). 이전 QR을 위해 전화번호도
 * 받는다. 연수자는 전화번호가 필요하다. 어떤 값이 필요한지는 서비스가 `authority`와 함께 확인한다.
 */
data class ScanEntryReqDto(
    val authority: EntryAuthority,
    @field:Size(max = 30)
    val phoneNumber: String? = null,
    @field:Positive
    val participantId: Long? = null,
    @field:Size(min = 1, max = 22)
    val code: String? = null,
) {
    // 전화번호와 코드가 로그에 남지 않게 한다
    override fun toString() = "ScanEntryReqDto(authority=$authority, participantId=$participantId)"
}
