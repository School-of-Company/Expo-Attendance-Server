package team.startup.expo.global.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import team.startup.expo.domain.qr.repository.QrEntryRepository

/**
 * `attendance.qr.entry.pending`: 박람회 서비스 장애 때 기간 확인 없이 기록해 아직 다시 확인하지 못한 종이 QR 입장 수(#101).
 * 오래 줄지 않으면 박람회 서비스가 계속 응답하지 못하거나 재확인 작업이 멈춘 것이다. 수집할 때마다 부분 인덱스로 센다.
 * 조회가 실패하면 NaN을 돌려줘 지표가 0으로 보이지 않게 한다.
 */
@Configuration
class PendingQrEntryMetrics {
    @Bean
    fun pendingQrEntryMetricsBinder(qrEntryRepository: QrEntryRepository): MeterBinder =
        MeterBinder { registry ->
            Gauge
                .builder("attendance.qr.entry.pending", qrEntryRepository) { pendingCount(it) }
                .description("기간 확인을 기다리는 종이 QR 입장 수")
                .register(registry)
        }

    private fun pendingCount(repository: QrEntryRepository): Double =
        try {
            repository.countPending().toDouble()
        } catch (e: Exception) {
            Double.NaN
        }
}
