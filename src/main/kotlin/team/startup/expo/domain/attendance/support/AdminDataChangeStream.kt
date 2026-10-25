package team.startup.expo.domain.attendance.support

import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.startup.expo.domain.attendance.presentation.dto.response.AdminDataChangeEvent
import team.startup.expo.domain.attendance.presentation.dto.response.AdminDataChangeResource
import team.startup.expo.global.config.AdminEventsProperties
import team.startup.expo.global.sse.SseConnectionRegistry
import team.startup.expo.global.sse.SseLimits
import team.startup.expo.global.sse.SseMessage
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 박람회별 실시간 입·퇴장 현황 변경 신호(#141). 연결 관리는 공통 [SseConnectionRegistry]가 맡는다.
 *
 * 입구에서는 1초에 여러 번 스캔이 커밋되므로, 같은 박람회 변경은 `coalesceInterval` 동안 모아 한 번만 보낸다.
 * 첫 변경 뒤 그 시간이 지나면 보내고, 그 사이의 변경은 같은 이벤트에 합쳐진다. 신호를 버리지는 않는다.
 */
@Component
@ConditionalOnProperty("admin-events.enabled", havingValue = "true")
class AdminDataChangeStream(
    mapper: ObjectMapper,
    private val clock: Clock,
    private val properties: AdminEventsProperties,
) : DisposableBean {
    private val registry =
        SseConnectionRegistry<String>(
            "attendance-admin-data",
            SseLimits(
                properties.maxConnections,
                properties.maxConnectionsPerExpo,
                properties.connectionLifetime,
                properties.heartbeatInterval,
            ),
            mapper::writeValueAsString,
        )
    private val scheduled = ConcurrentHashMap.newKeySet<String>()
    private val coalescer =
        ScheduledThreadPoolExecutor(1) { Thread(it, "attendance-admin-data-coalesce").apply { isDaemon = true } }

    fun open(expoId: String): SseEmitter = registry.open(expoId)

    fun changed(expoId: String) {
        // 구독자가 없는 박람회의 변경은 보낼 곳이 없다
        if (registry.connectionCount(expoId) == 0) return
        val delay = properties.coalesceInterval.toMillis()
        if (delay <= 0) return registry.publish(expoId, message(expoId))
        if (!scheduled.add(expoId)) return
        coalescer.schedule({
            // 보내기 전에 지워, 보내는 동안 들어온 변경은 다음 이벤트로 예약된다
            scheduled.remove(expoId)
            registry.publish(expoId, message(expoId))
        }, delay, TimeUnit.MILLISECONDS)
    }

    /** 전달 경로가 끊겼다 복구되면 그 사이 신호를 잃었을 수 있으므로 모든 구독자에게 재조회를 알린다. */
    fun resyncAll() = registry.publishAll(::message)

    fun connectionCount(expoId: String? = null): Int = registry.connectionCount(expoId)

    override fun destroy() {
        coalescer.shutdownNow()
        registry.close()
    }

    private fun message(expoId: String): SseMessage {
        val event = AdminDataChangeEvent(UUID.randomUUID(), expoId, AdminDataChangeResource.ATTENDANCE, clock.instant())
        return SseMessage(event.eventId.toString(), EVENT_NAME, event)
    }

    companion object {
        const val EVENT_NAME = "admin-data-changed"
    }
}
