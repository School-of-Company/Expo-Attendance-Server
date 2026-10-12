package team.startup.expo.global.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 서비스 지표 기록(#101). 다른 서비스 호출([team.startup.expo.global.client.callService])과 취소 보호 락 대기처럼 스프링 빈이 아닌
 * 확장 함수나 생성자가 고정된 클래스에서도 기록할 수 있게 레지스트리를 한 곳에 둔다. 레지스트리가 아직 없으면(단위 테스트 등) 아무것도 하지 않는다.
 *
 * 태그에는 대상 서비스와 결과 같은 값만 쓰고, 요청·응답 내용, 참가자·박람회 ID, 전화번호는 넣지 않는다(라벨 값이 늘어나지 않게).
 */
@Component
class AttendanceMetrics(
    meterRegistry: MeterRegistry,
) {
    init {
        registry = meterRegistry
    }

    companion object {
        @Volatile
        private var registry: MeterRegistry? = null

        /** 테스트가 레지스트리를 바꾸거나 비울 때 쓴다. */
        fun use(meterRegistry: MeterRegistry?) {
            registry = meterRegistry
        }

        private val serviceNames = mapOf("유저" to "user", "박람회" to "expo", "신청" to "application")

        /** 다른 서비스 호출 한 번. `outcome`은 success, not_found, conflict, circuit_open, failure 중 하나다. */
        fun recordDownstream(
            service: String,
            outcome: String,
            nanos: Long,
        ) {
            val target = registry ?: return
            Timer
                .builder("attendance.downstream.calls")
                .description("다른 서비스 호출의 소요 시간과 결과")
                .tag("service", serviceNames[service] ?: service)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(target)
                .record(nanos, TimeUnit.NANOSECONDS)
        }

        /** 취소 보호 락을 얻기까지 기다린 시간. `kind`는 scan(스캔이 잡는 공유 락)과 cancel(취소 검사가 잡는 배타 락)이다. */
        fun recordLockWait(
            kind: String,
            nanos: Long,
        ) {
            val target = registry ?: return
            Timer
                .builder("attendance.guard.lock.wait")
                .description("취소 보호 락을 얻기까지 기다린 시간")
                .tag("kind", kind)
                .publishPercentileHistogram()
                .register(target)
                .record(nanos, TimeUnit.NANOSECONDS)
        }
    }
}
