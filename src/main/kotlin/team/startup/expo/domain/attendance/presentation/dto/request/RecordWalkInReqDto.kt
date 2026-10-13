package team.startup.expo.domain.attendance.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import team.startup.expo.domain.attendance.entity.WalkInAction
import team.startup.expo.domain.attendance.entity.WalkInCategory

@Schema(description = "QR 없는 방문객 간편입장 증감 요청")
data class RecordWalkInReqDto(
    @field:Schema(description = "구분", example = "ADULT")
    val category: WalkInCategory,
    @field:Schema(description = "`ADD`는 +1, `UNDO`는 잘못 누른 것을 하나 되돌린다(합계는 0 아래로 내려가지 않는다). 없으면 `ADD`", example = "ADD", defaultValue = "ADD")
    val action: WalkInAction = WalkInAction.ADD,
    @field:Schema(
        description = "같은 요청이 응답 유실로 다시 가도 한 번만 세게 하는 키(영문·숫자·`-`·`_`, 최대 64자). 보내지 않으면 재전송을 걸러내지 않는다",
        example = "9f4c1d2e-0b7a-4a55-9c1e-6a5d5f3b2c10",
        nullable = true,
    )
    @field:Size(max = 64)
    @field:Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "requestId는 영문·숫자·-·_만 쓸 수 있습니다.")
    val requestId: String? = null,
)
