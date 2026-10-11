package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.CancellationStatusResDto
import team.startup.expo.domain.attendance.service.GetCancellationStatusService

/** 신청 서비스 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. 전화번호는 URL이 아니라 본문으로 받는다. */
@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/cancellation-status")
class InternalCancellationStatusController(
    private val getCancellationStatusService: GetCancellationStatusService,
) {
    @Operation(
        summary = "참가자 취소 상태 조회(읽기 전용)",
        description =
            "신청 서비스가 신청별 취소 가능 여부를 계산하는 데 쓰는 시점 조회입니다. 아무것도 기록하거나 잠그지 않습니다. " +
                "입장 여부(유저 서비스), 일반·연결된 연수자의 프로그램 출석 여부, 취소 보호 상태를 돌려줍니다. " +
                "유저 서비스가 모르는 참가자·번호 조합은 구분 없이 404입니다. 최종 취소는 취소 보호 acquire로 다시 확인합니다.",
    )
    @PostMapping
    fun get(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @Valid @RequestBody reqDto: GetCancellationStatusReqDto,
    ): CancellationStatusResDto = getCancellationStatusService.get(expoId, participantId, reqDto)
}
