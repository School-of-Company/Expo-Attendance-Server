package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.startup.expo.domain.attendance.service.SubscribeAdminDataChangesService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.swagger.ApiError

/** 실시간 입·퇴장 현황 변경 구독(#141). 관리자(`ROLE_ADMIN`) 전용이며 `admin-events.enabled`일 때만 있다. */
@RestController
@ConditionalOnProperty("admin-events.enabled", havingValue = "true")
class AdminDataChangeController(
    private val subscribeAdminDataChangesService: SubscribeAdminDataChangesService,
) {
    @Operation(
        summary = "실시간 입·퇴장 현황 변경 구독(SSE)",
        description =
            "`admin-data-changed` 이벤트를 보낸다. data는 eventId, expoId, resource(ATTENDANCE), occurredAt뿐이며 받으면 " +
                "`/attendance/summary/{expoId}`와 `/hourly`, `/recent`, `/scanners`를 현재 필터로 다시 조회한다. 같은 박람회 변경은 " +
                "`coalesceInterval`(기본 1초) 동안 모아 한 번 보낸다. 연결·재연결 직후에도 기존 GET으로 전체 값을 다시 읽는다. " +
                "이벤트를 재생하지 않으며 Last-Event-ID는 쓰지 않는다. heartbeat는 SSE 주석이다. 연결 수명(기본 30분)이 끝나면 서버가 닫는다. " +
                "query 파라미터는 받지 않는다.",
    )
    @ApiError(400, "박람회 ID 형식(소문자 UUID)이 올바르지 않거나 query 파라미터를 보냈다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "실시간 연결 수 한도를 넘었다. 잠시 후 다시 연결한다.")
    @GetMapping("/attendance/summary/{expoId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(
        @PathVariable expoId: String,
        request: HttpServletRequest,
    ): ResponseEntity<SseEmitter> {
        // 브라우저 EventSource용 토큰 query를 받지 않는다. 인증은 게이트웨이가 Authorization 헤더로 한다
        if (!request.queryString.isNullOrEmpty()) throw ExpectedException(HttpStatus.BAD_REQUEST, "query 파라미터를 받지 않습니다.")
        val emitter = subscribeAdminDataChangesService.subscribe(expoId)
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            // nginx 계열 프록시가 이벤트를 모아 두지 않도록 한다
            .header("X-Accel-Buffering", "no")
            .body(emitter)
    }
}
