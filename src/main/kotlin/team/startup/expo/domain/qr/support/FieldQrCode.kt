package team.startup.expo.domain.qr.support

import team.startup.expo.domain.qr.entity.QrCategory

/**
 * 장학사가 미리 만들어 리플렛에 붙여 둔 종이 QR의 코드(#243). 성인은 `ADT-0001`~`ADT-0500`, 어린이는 `KID-0001`~`KID-0500`이고,
 * 확인 문자가 없으며 QR에 코드 글자가 그대로 들어 있다. 서버가 발급한 `ADT-0001-K7` 형식([team.startup.expo.domain.qr.entity.QrTokenCode])과
 * 따로 쓴다.
 *
 * `tb_qr_token.token`은 전체에서 유일한 키라 박람회마다 같은 코드가 겹치지 않도록, 저장하는 토큰은 `<박람회ID>:<코드>`로 만든다.
 */
class FieldQrCode private constructor(
    val code: String,
    val category: QrCategory,
) {
    fun token(expoId: String): String = "$expoId:$code"

    companion object {
        const val MAX_SERIAL = 500
        private val FORMAT = Regex("^(ADT|KID)-(\\d{4})$")

        /** 규칙에 맞는 코드(접두사와 4자리 연번이 범위 안)면 정규화한 값을, 아니면 null을 돌려준다. */
        fun parse(input: String): FieldQrCode? {
            val code = input.trim().uppercase()
            val match = FORMAT.matchEntire(code) ?: return null
            if (match.groupValues[2].toInt() !in 1..MAX_SERIAL) return null
            val category = if (match.groupValues[1] == "ADT") QrCategory.ADULT else QrCategory.CHILD
            return FieldQrCode(code, category)
        }

        /** 입력이 현장 코드면 저장 토큰으로, 아니면 입력 그대로 돌려준다. */
        fun tokenOf(
            expoId: String,
            input: String,
        ): String = parse(input)?.token(expoId) ?: input
    }
}
