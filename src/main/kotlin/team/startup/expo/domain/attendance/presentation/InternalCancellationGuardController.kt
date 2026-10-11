package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.AcquireCancellationGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.CancellationGuardBlockedResDto
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.attendance.service.CancellationGuardService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.swagger.ApiError

/** 신청 서비스 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. */
@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/cancellation-guard/{operationId}")
class InternalCancellationGuardController(
    private val cancellationGuardService: CancellationGuardService,
) {
    @Operation(
        summary = "신청 취소 보호 걸기",
        description =
            "진행 중인 입장·프로그램 스캔이 끝나기를 기다린 뒤 이 참가자의 스캔을 막고, 입장 기록과 프로그램 출석이 없을 때만 유지합니다. " +
                "204는 보호를 걸었다는 뜻이고, 이미 입장했거나 출석했으면 보호를 걸지 않고 409(`entered`, `programAttended`)입니다. " +
                "같은 operationId는 멱등이고 다른 작업이 이미 걸었으면 409입니다. 시간이 지나도 자동으로 풀리지 않습니다.",
    )
    @ApiResponse(responseCode = "204", description = "보호를 걸었다(같은 operationId로 이미 걸려 있거나 확정된 경우 포함).")
    @ApiResponse(
        responseCode = "409",
        description =
            "이미 입장했거나 프로그램에 출석해 보호를 걸지 않았다(본문 `CancellationGuardBlockedResDto`). " +
                "다른 operationId의 작업이 이미 걸었거나 확정한 경우에는 `ErrorResponse`다.",
        content = [Content(schema = Schema(implementation = CancellationGuardBlockedResDto::class))],
    )
    @ApiError(400, "operationId 형식(영문·숫자·`-`, 1~64자)이나 요청 값이 올바르지 않다.")
    @ApiError(404, "삭제된 박람회이거나 참가자를 찾지 못했다.")
    @ApiError(503, "진행 중인 스캔이 끝나지 않았거나 유저 서비스를 사용할 수 없다. 같은 operationId로 다시 시도한다.")
    @PutMapping
    fun acquire(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable operationId: String,
        @Valid @RequestBody reqDto: AcquireCancellationGuardReqDto,
    ): ResponseEntity<CancellationGuardBlockedResDto> {
        checkOperationId(operationId)
        return when (val result = cancellationGuardService.acquire(expoId, participantId, operationId, reqDto)) {
            AcquireCancellationGuardResult.Acquired -> {
                ResponseEntity.noContent().build()
            }

            is AcquireCancellationGuardResult.Blocked -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(CancellationGuardBlockedResDto(result.entered, result.programAttended))
            }
        }
    }

    @Operation(summary = "신청 취소 확정", description = "취소를 확정합니다. 재신청으로 회차가 다시 기록될 때까지 스캔을 계속 막습니다. 같은 operationId는 멱등입니다.")
    @ApiError(400, "operationId 형식이 올바르지 않다.")
    @ApiError(409, "이 operationId의 취소 보호가 없다. 먼저 보호를 걸어야 한다.")
    @ApiError(503, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해야 한다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/commit")
    fun commit(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable operationId: String,
    ) {
        checkOperationId(operationId)
        cancellationGuardService.commit(expoId, participantId, operationId)
    }

    @Operation(summary = "신청 취소 중단", description = "취소를 중단하고 스캔을 다시 허용합니다. 없는 작업은 204이고 이미 확정된 작업은 409입니다.")
    @ApiError(400, "operationId 형식이 올바르지 않다.")
    @ApiError(409, "이미 확정된 취소 작업은 중단할 수 없다.")
    @ApiError(503, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해야 한다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    fun abort(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable operationId: String,
    ) {
        checkOperationId(operationId)
        cancellationGuardService.abort(expoId, participantId, operationId)
    }

    private fun checkOperationId(operationId: String) {
        if (!OPERATION_ID.matches(operationId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "operationId 형식이 올바르지 않습니다.")
    }

    private companion object {
        val OPERATION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
