package team.startup.expo.domain.attendance.service.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.startup.expo.domain.attendance.service.SubscribeAdminDataChangesService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.sse.SseConnectionLimitException

@Service
@ConditionalOnProperty("admin-events.enabled", havingValue = "true")
class SubscribeAdminDataChangesServiceImpl(
    private val stream: AdminDataChangeStream,
) : SubscribeAdminDataChangesService {
    override fun subscribe(expoId: String): SseEmitter {
        if (!EXPO_ID_PATTERN.matches(expoId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.")
        return try {
            stream.open(expoId)
        } catch (_: SseConnectionLimitException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "실시간 연결이 많습니다. 잠시 후 다시 연결해 주세요.")
        }
    }

    private companion object {
        val EXPO_ID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
