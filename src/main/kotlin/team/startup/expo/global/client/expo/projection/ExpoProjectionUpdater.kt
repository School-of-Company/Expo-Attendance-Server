package team.startup.expo.global.client.expo.projection

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 메시지 하나로 그 박람회의 프로젝션 전체를 덮어쓴다(부분 병합하지 않는다). 저장된 version 이하인 메시지(중복 `eventId`, 늦게 온
 * 옛 메시지, 재발행과 섞인 메시지)는 버린다. tombstone은 프로그램·회차를 지우고 머리 행에 version만 남긴다.
 */
@Component
class ExpoProjectionUpdater(
    private val repository: ExpoProjectionRepository,
) {
    /** 반영했으면 `true`, 옛 메시지라 버렸으면 `false`. */
    @Transactional
    fun apply(event: ExpoChangedEvent): Boolean {
        if (!repository.upsertHead(event)) return false
        repository.replaceChildren(event)
        return true
    }
}
