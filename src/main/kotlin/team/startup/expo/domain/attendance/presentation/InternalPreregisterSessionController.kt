package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.AssignPreregisterSessionReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CancelPreregisterSessionReqDto
import team.startup.expo.domain.attendance.service.PreregisterSessionService
import team.startup.expo.global.swagger.ApiError

/** 신청 서비스 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. */
@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/preregister-session")
class InternalPreregisterSessionController(
    private val preregisterSessionService: PreregisterSessionService,
) {
    @Operation(
        summary = "신청 회차 기록",
        description =
            "확정·승급한 참가자의 회차를 기록합니다. 같은 요청을 다시 보내도 결과는 같고, " +
                "취소된 참가자가 재신청해 확정되면 다시 입장할 수 있습니다. " +
                "revision을 보내면 더 오래된 요청은 상태를 바꾸지 않고 204이며, 같은 revision에 다른 내용이면 409입니다.",
    )
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "삭제된 박람회다.")
    @ApiError(409, "같은 revision에 다른 내용의 요청이 이미 반영되었다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping
    fun assign(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @Valid @RequestBody reqDto: AssignPreregisterSessionReqDto,
    ) = preregisterSessionService.assign(expoId, participantId, reqDto)

    @Operation(
        summary = "신청 취소 표시",
        description =
            "취소된 신청의 QR·전화번호 입장을 막습니다. revision 없이 보내면 기록이 없을 때 아무것도 하지 않고 204입니다. " +
                "revision을 보내면 기록이 없어도 취소를 남기고, 더 오래된 요청은 상태를 바꾸지 않고 204이며, " +
                "같은 revision에 다른 내용이면 409입니다.",
    )
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(409, "같은 revision에 다른 내용의 요청이 이미 반영되었다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    fun cancel(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @Valid @ModelAttribute reqDto: CancelPreregisterSessionReqDto,
    ) = preregisterSessionService.cancel(expoId, participantId, reqDto)
}
