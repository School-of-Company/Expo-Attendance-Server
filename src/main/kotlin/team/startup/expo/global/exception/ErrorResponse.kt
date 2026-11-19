package team.startup.expo.global.exception

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "오류 응답. 모든 엔드포인트가 같은 형식으로 오류를 돌려준다")
data class ErrorResponse(
    @field:Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @field:Schema(description = "오류 메시지", example = "잘못된 요청입니다.")
    val message: String,
    @field:Schema(description = "분기용 사유 코드. 있는 오류만 싣는다(기능 OFF 409는 `FEATURE_DISABLED`). 없으면 필드를 생략한다", example = "FEATURE_DISABLED")
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val code: String? = null,
)
