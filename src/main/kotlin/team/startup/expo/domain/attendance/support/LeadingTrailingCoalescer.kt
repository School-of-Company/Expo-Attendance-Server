package team.startup.expo.domain.attendance.support

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * 같은 키(박람회)의 신호를 `intervalMillis` 안에 한 번만 내보낸다(#269).
 *
 * 조용하던 뒤의 첫 신호는 바로 내보내고(leading), 그 뒤 간격 안에 들어온 신호는 하나로 합쳐 간격이 끝날 때 한 번 내보낸다(trailing).
 * 그래서 키마다 간격 안에는 많아야 한 번 나가고, 마지막 신호는 반드시 나간다.
 * 상태는 키마다 잠그고, 내보내기(`send`)와 예약(`schedule`)은 잠금 밖에서 한다.
 *
 * @param schedule 지연(ms) 뒤에 작업을 한 번 실행한다. 테스트는 제어 가능한 구현을 넣는다.
 */
class LeadingTrailingCoalescer(
    private val clock: Clock,
    private val intervalMillis: Long,
    private val schedule: (delayMillis: Long, task: Runnable) -> Unit,
    private val send: (String) -> Unit,
) {
    private class State {
        var lastSentAt: Long? = null
        var trailingPending = false
    }

    private val states = ConcurrentHashMap<String, State>()

    fun signal(key: String) {
        if (intervalMillis <= 0) return send(key)
        val state = states.computeIfAbsent(key) { State() }
        var delay = -1L
        var sendNow = false
        synchronized(state) {
            // 뒤쪽 발송이 이미 예약돼 있으면 그 이벤트에 합쳐진다
            if (state.trailingPending) return
            val now = clock.millis()
            val last = state.lastSentAt
            if (last == null || now - last >= intervalMillis) {
                state.lastSentAt = now
                sendNow = true
            } else {
                state.trailingPending = true
                delay = last + intervalMillis - now
            }
        }
        if (sendNow) {
            send(key)
        } else {
            schedule(delay) { sendTrailing(key, state) }
        }
    }

    private fun sendTrailing(
        key: String,
        state: State,
    ) {
        // 보내기 전에 지워, 보내는 동안 들어온 신호는 다음 간격에서 처리된다
        synchronized(state) {
            state.trailingPending = false
            state.lastSentAt = clock.millis()
        }
        send(key)
    }
}
