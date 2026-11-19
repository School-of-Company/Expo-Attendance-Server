package team.startup.expo.global.exception

import org.springframework.http.HttpStatus

class ExpectedException(
    val status: HttpStatus,
    override val message: String,
    /** 응답 `code`. 분기용 사유 코드가 있는 오류만 싣는다(예: `FEATURE_DISABLED`). */
    val code: String? = null,
) : RuntimeException(message)
