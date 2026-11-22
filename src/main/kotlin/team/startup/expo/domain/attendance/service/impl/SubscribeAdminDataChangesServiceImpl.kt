package team.startup.expo.domain.attendance.service.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.startup.expo.domain.attendance.service.SubscribeAdminDataChangesService
import team.startup.expo.domain.attendance.support.AdminDataChangeStream
import team.startup.expo.domain.attendance.support.ExpoIdFormat
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.sse.SseConnectionLimitException

@Service
@ConditionalOnProperty("admin-events.enabled", havingValue = "true")
class SubscribeAdminDataChangesServiceImpl(
    private val stream: AdminDataChangeStream,
) : SubscribeAdminDataChangesService {
    override fun subscribe(expoId: String): SseEmitter {
        ExpoIdFormat.requireValid(expoId)
        return try {
            stream.open(expoId)
        } catch (_: SseConnectionLimitException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "실시간 연결이 많습니다. 잠시 후 다시 연결해 주세요.")
        }
    }
}
