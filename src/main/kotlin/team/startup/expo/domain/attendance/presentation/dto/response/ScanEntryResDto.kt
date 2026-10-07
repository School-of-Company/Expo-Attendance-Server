package team.startup.expo.domain.attendance.presentation.dto.response

data class ScanEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val personalInformationStatus: Boolean,
    val participationType: String,
    /** 명찰 출력 대상(연수자 전원, 교사인 일반 참가자)이 아니면 `null`이다. */
    val badge: BadgeResDto?,
) {
    override fun toString() = "ScanEntryResDto(id=$id, participationType=$participationType)"
}

data class BadgeResDto(
    val name: String,
    val school: String?,
    /** 입구 스캔 QR과 같은 v1 형식의 JSON 문자열이다. 이름·소속은 담지 않는다. */
    val qrCode: String,
)
