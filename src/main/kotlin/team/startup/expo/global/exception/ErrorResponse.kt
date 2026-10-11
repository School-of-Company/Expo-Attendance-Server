package team.startup.expo.global.exception

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "오류 응답. 모든 엔드포인트가 같은 형식으로 오류를 돌려준다")
data class ErrorResponse(
    @field:Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @field:Schema(description = "오류 메시지", example = "잘못된 요청입니다.")
    val message: String,
)
