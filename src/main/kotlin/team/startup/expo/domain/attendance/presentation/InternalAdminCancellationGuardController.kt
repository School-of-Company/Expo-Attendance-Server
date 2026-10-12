package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.response.CancellationGuardBlockedResDto
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.attendance.service.CancellationGuardService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.swagger.ApiError

/**
 * 신청 서비스 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. 관리자 프로그램 신청 취소(#116)가 참가자 본인 전화 인증 없이
 * 입장·출석 후 제한과 스캔 경합 보호를 지키게 한다. **관리자 쓰기 권한 검증은 호출하는 신청 서비스의 몫**이고 이 서비스는 내부 호출자 인증,
 * 박람회·참가자 범위, 스캔 보호만 맡는다. 확정(commit)과 중단(abort)은 기존 `cancellation-guard/{operationId}` 경로를 같은 operationId로 쓴다.
 */
@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/admin-cancellation-guard/{operationId}")
class InternalAdminCancellationGuardController(
    private val cancellationGuardService: CancellationGuardService,
) {
    @Operation(
        summary = "관리자 신청 취소 보호 걸기",
        description =
            "참가자 본인 전화 인증 없이(신청 서비스가 관리자 권한을 검증한 전제) 진행 중인 입장·프로그램 스캔이 끝나기를 기다린 뒤 이 참가자의 스캔을 막고, " +
                "입장 기록과 프로그램 출석이 없을 때만 유지합니다. 의미는 `PUT cancellation-guard/{operationId}`와 같습니다(204 보호됨, " +
                "이미 입장·출석이면 보호 없이 409 `entered`/`programAttended`, 다른 작업이 걸려 있으면 409, 중단된 operationId는 409, 유저가 모르면 404, " +
                "유저 장애는 503이며 같은 operationId로 재시도). 프로그램 신청 하나만 취소할 때는 로컬 취소 뒤 `DELETE cancellation-guard/{operationId}`(abort)로 " +
                "끝냅니다. commit은 재신청 전까지 참가자의 모든 스캔을 막으므로 쓰지 않습니다.",
    )
    @ApiResponse(responseCode = "204", description = "보호를 걸었다(같은 operationId로 이미 걸려 있거나 확정된 경우 포함).")
    @ApiResponse(
        responseCode = "409",
        description =
            "이미 입장했거나 프로그램에 출석해 보호를 걸지 않았다(본문 `CancellationGuardBlockedResDto`). " +
                "다른 operationId의 작업이 이미 걸었거나 확정했거나 이 operationId가 이미 중단된 경우에는 `ErrorResponse`다.",
        content = [Content(schema = Schema(implementation = CancellationGuardBlockedResDto::class))],
    )
    @ApiError(400, "operationId 형식(영문·숫자·`-`, 1~64자)이 올바르지 않다.")
    @ApiError(404, "삭제된 박람회이거나 참가자를 찾지 못했다(다른 박람회의 참가자 포함).")
    @ApiError(503, "진행 중인 스캔이 끝나지 않았거나 유저 서비스를 사용할 수 없다. 같은 operationId로 다시 시도한다.")
    @PutMapping
    fun acquire(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable operationId: String,
    ): ResponseEntity<CancellationGuardBlockedResDto> {
        if (!OPERATION_ID.matches(operationId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "operationId 형식이 올바르지 않습니다.")
        return when (val result = cancellationGuardService.acquireForAdmin(expoId, participantId, operationId)) {
            AcquireCancellationGuardResult.Acquired -> {
                ResponseEntity.noContent().build()
            }

            is AcquireCancellationGuardResult.Blocked -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(CancellationGuardBlockedResDto(result.entered, result.programAttended))
            }
        }
    }

    private companion object {
        val OPERATION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
