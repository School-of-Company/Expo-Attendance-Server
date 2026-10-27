package team.startup.expo.domain.attendance.support

/**
 * 목록 화면에 싣는 전화번호를 가린다(#237). 하루 입장자 전원의 번호가 한 응답에 모이므로 가운데를 가리고 앞 3자리와 끝 4자리만 남긴다
 * (`010-****-5678`). 저장 표기(하이픈 유무)와 상관없이 숫자만 본다.
 */
object PhoneNumberMask {
    fun mask(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        val digits = phoneNumber.filter(Char::isDigit)
        return when {
            digits.length >= 10 -> "${digits.take(3)}-****-${digits.takeLast(4)}"
            digits.length >= 4 -> "****-${digits.takeLast(4)}"
            else -> "****"
        }
    }
}
