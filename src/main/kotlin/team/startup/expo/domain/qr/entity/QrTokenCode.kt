package team.startup.expo.domain.qr.entity

import java.util.zip.CRC32

/**
 * 종이에 인쇄하는 연번 코드. `ADT-0001-K7`은 구분 접두사(`ADT`), 연번(`0001`), 확인 문자 두 자리(`K7`)다.
 * 확인 문자는 접두사와 연번의 CRC32에서 만들어, 분실 QR을 손으로 입력할 때의 오타를 걸러 낸다(비밀이 아니다).
 * 헷갈리는 `I`, `O`는 쓰지 않는다.
 */
object QrTokenCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val FORMAT = Regex("^([A-Z]{3})-(\\d{4,})-([0-9A-Z]{2})$")

    fun of(
        category: QrCategory,
        serial: Int,
    ): String {
        val body = "${category.codePrefix}-${serial.toString().padStart(4, '0')}"
        return "$body-${checkChars(body)}"
    }

    /** 입력을 대문자로 맞추고 형식과 확인 문자가 맞으면 정규화한 코드를, 아니면 null을 돌려준다. */
    fun normalize(input: String): String? {
        val code = input.trim().uppercase()
        val match = FORMAT.matchEntire(code) ?: return null
        val body = "${match.groupValues[1]}-${match.groupValues[2]}"
        return code.takeIf { match.groupValues[3] == checkChars(body) }
    }

    private fun checkChars(body: String): String {
        val value = CRC32().also { it.update(body.toByteArray()) }.value % (ALPHABET.length * ALPHABET.length)
        return "${ALPHABET[(value / ALPHABET.length).toInt()]}${ALPHABET[(value % ALPHABET.length).toInt()]}"
    }
}
