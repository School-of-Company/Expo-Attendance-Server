package team.startup.expo.domain.attendance.service

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface SubscribeAdminDataChangesService {
    fun subscribe(expoId: String): SseEmitter
}
