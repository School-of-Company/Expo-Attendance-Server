package team.startup.expo.domain.attendance.service

import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.global.config.EntryReplayProperties
import java.time.Duration
import java.time.LocalDateTime

/**
 * 같은 날 퇴장 없이 다시 찍은 입장 스캔을 400 대신 직전 입장 결과로 재생할지 판단한다(#176). 스캐너 요청이 타임아웃으로 끊겨도
 * 서버는 입장을 기록하므로, 재시도가 문 앞에서 실패로 보이지 않게 한다. 오늘 입장 로그 행이 있고, 퇴장하지 않았고, 입장 시각이
 * `windowSeconds` 이내이고, 스캐너 이름이 양쪽 모두 있으면 같아야 한다(한쪽이라도 없으면 일치로 본다).
 * 재생은 아무것도 기록하지 않는다. 취소·폐기·기간 검사는 호출하는 쪽이 이 판단 앞에서 이미 마친다.
 */
@Component
class EntryReplayPolicy(
    private val properties: EntryReplayProperties,
) {
    fun allows(
        log: EntryLog?,
        now: LocalDateTime,
        scanner: String?,
    ): Boolean {
        if (properties.windowSeconds <= 0 || log == null || log.exitedAt != null) return false
        val age = Duration.between(log.enteredAt, now)
        if (age.isNegative || age > Duration.ofSeconds(properties.windowSeconds)) return false
        return scanner == null || log.scanner == null || scanner == log.scanner
    }
}
