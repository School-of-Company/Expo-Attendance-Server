package team.startup.expo.global.logging

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerMapping

private val logger = LoggerFactory.getLogger("team.startup.expo.global.logging.ScanRejection")

/**
 * 업무 사유로 거절한 요청(4xx)을 한 줄로 남긴다. 현장에서 "왜 안 들어가졌는지"를 되짚기 위한 것으로,
 * 박람회·참가자 ID(MDC)와 경로 템플릿, 상태 코드, 거절 사유(고정된 한국어 문장)만 담는다.
 * 경로의 실제 값, 요청 본문, 전화번호, 코드, 이름은 남기지 않는다.
 *
 * 입구에서 분당 수백 건이 찍혀도 한 줄씩이라 `info`로 둔다. 시끄러우면 이 로거만 올리면 된다
 * (`logging.level.team.startup.expo.global.logging.ScanRejection=warn`).
 */
fun logRejection(
    request: HttpServletRequest,
    status: HttpStatus,
    reason: String?,
) {
    if (!status.is4xxClientError || !logger.isInfoEnabled) return
    val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: "unknown"
    logger.info(
        "요청 거절: {} {} status={} expoId={} participantId={} reason={}",
        request.method,
        route,
        status.value(),
        MDC.get(RequestContext.EXPO_ID) ?: "-",
        MDC.get(RequestContext.PARTICIPANT_ID) ?: "-",
        reason,
    )
}
