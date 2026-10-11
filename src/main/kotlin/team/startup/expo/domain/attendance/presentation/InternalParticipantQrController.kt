package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto
import team.startup.expo.domain.attendance.service.GetParticipantQrService

/** 신청 서비스 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. 대표자 세션과 소유권은 호출하는 쪽이 먼저 확인한다. */
@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/qr")
class InternalParticipantQrController(
    private val getParticipantQrService: GetParticipantQrService,
) {
    @Operation(
        summary = "확정된 참가자의 QR 값 조회",
        description =
            "신청이 확정된 참가자의 QR 값(`participantId`, `code`)을 돌려줍니다. 회차 기록이 없거나 취소된 참가자, " +
                "대표자의 일행이 아닌 참가자는 구분 없이 404입니다. 아무것도 기록하지 않고 `code`는 로그에 남기지 않습니다.",
    )
    @PostMapping
    fun get(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @Valid @RequestBody reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto = getParticipantQrService.get(expoId, participantId, reqDto)
}
