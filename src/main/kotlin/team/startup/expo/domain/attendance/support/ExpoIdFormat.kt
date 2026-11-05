package team.startup.expo.domain.attendance.support

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException

/** 박람회 ID 형식(소문자 UUID). 형식을 확인하는 곳은 모두 이 규칙과 문구를 쓴다. */
object ExpoIdFormat {
    private val PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun isValid(expoId: String): Boolean = PATTERN.matches(expoId)

    /** 형식이 맞지 않으면 400이다. */
    fun requireValid(expoId: String) {
        if (!isValid(expoId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.")
    }
}
