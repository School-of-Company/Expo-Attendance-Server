package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.service.FieldVisitService
import team.startup.expo.global.swagger.ApiError
import java.util.UUID

@RestController
@RequestMapping("/internal/expos/{expoId}/participants/{participantId}/field-visits/{registrationId}")
class InternalFieldVisitController(
    private val service: FieldVisitService,
) {
    @Operation(summary = "날짜별 현장등록 projection", description = "이전 세대는 204로 무시하며 같은 세대의 취소는 최종 상태입니다.")
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "삭제된 박람회다.")
    @ApiError(409, "등록 ID 또는 작업 내용이 일치하지 않는다.")
    @ApiError(503, "잠금을 얻을 수 없다.")
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun project(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @Valid @RequestBody request: ProjectFieldVisitReqDto,
    ) = service.project(expoId, participantId, registrationId, request)

    @Operation(summary = "방문일별 취소 가능 상태", description = "소유 확인 후 대상 날짜의 최초 입장·출석·보호 상태와 대상 식별자를 반환합니다.")
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "등록 또는 소유 참가자가 없다.")
    @ApiError(409, "대상 날짜 또는 세대가 일치하지 않는다.")
    @ApiError(503, "입장 결과가 불확실하거나 공급자 또는 잠금을 사용할 수 없다.")
    @PostMapping("/cancellation-status")
    fun status(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @Valid @RequestBody request: FieldVisitCancellationReqDto,
    ) = service.status(expoId, participantId, registrationId, request)

    @Operation(summary = "방문일 취소 보호 획득", description = "진행 중인 참가자·연결 연수자 스캔을 기다린 후 최초 입장과 출석을 재확인합니다.")
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "등록 또는 소유 참가자가 없다.")
    @ApiError(409, "입장·출석했거나 다른 작업 또는 중단된 작업이다.")
    @ApiError(503, "입장 결과가 불확실하거나 공급자 또는 잠금을 사용할 수 없다.")
    @PutMapping("/cancellation-guards/{operationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acquire(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @PathVariable operationId: UUID,
        @Valid @RequestBody request: FieldVisitCancellationReqDto,
    ) = service.acquire(expoId, participantId, registrationId, operationId, request)

    @Operation(summary = "방문일 취소 보호 확정", description = "해당 FIELD만 취소하며 PRE와 다른 방문일을 보존합니다. 이전 세대는 204로 무시합니다.")
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "등록 또는 박람회가 없다.")
    @ApiError(409, "대상 또는 보호 작업이 일치하지 않거나 중단되었다.")
    @ApiError(503, "잠금을 얻을 수 없다.")
    @PostMapping("/cancellation-guards/{operationId}/commit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun commit(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @PathVariable operationId: UUID,
        @Valid @RequestBody request: FinishFieldVisitGuardReqDto,
    ) = service.commit(expoId, participantId, registrationId, operationId, request)

    @Operation(summary = "방문일 취소 보호 중단", description = "중단 이력을 남겨 늦은 acquire를 막습니다. 이전 세대는 204로 무시합니다.")
    @ApiError(400, "요청 값이 올바르지 않다.")
    @ApiError(404, "등록 또는 박람회가 없다.")
    @ApiError(409, "대상이 일치하지 않거나 이미 확정된 작업이다.")
    @ApiError(503, "잠금을 얻을 수 없다.")
    @DeleteMapping("/cancellation-guards/{operationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun abort(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @PathVariable operationId: UUID,
        @Valid @RequestBody request: FinishFieldVisitGuardReqDto,
    ) = service.abort(expoId, participantId, registrationId, operationId, request)
}
