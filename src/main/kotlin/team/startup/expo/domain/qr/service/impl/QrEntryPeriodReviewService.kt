package team.startup.expo.domain.qr.service.impl

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import team.startup.expo.domain.qr.repository.PendingQrEntry
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 박람회 서비스가 응답하지 못해 기간 확인 없이 기록한 종이 QR 입장(`PENDING`)을 다시 확인한다. 입장한 날이 박람회 기간 안이면
 * `VERIFIED`, 밖이면 `OUT_OF_PERIOD`, 박람회가 없으면 `EXPO_NOT_FOUND`로 바꾼다. 입장 기록 자체는 지우지 않는다(운영이 보고 판단).
 * 박람회 서비스가 아직 응답하지 못하면 그 박람회의 입장은 `PENDING`으로 남겨 다음 주기에 다시 시도한다(박람회별로 처리하므로 한 박람회가 막혀도 다른 박람회는 진행된다). 토큰 값은 로그에 남기지 않는다.
 */
@Service
class QrEntryPeriodReviewService(
    private val qrEntryRepository: QrEntryRepository,
    private val expoPeriodValidator: ExpoPeriodValidator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 박람회마다 따로 처리하고 상태를 바꾼 건수를 돌려준다. 박람회 서비스가 응답하지 못하는 박람회는 한 번만 시도하고 넘어가므로,
     * 그 박람회의 입장이 아무리 많아도 다른 박람회의 확인을 막지 않는다.
     */
    fun reviewPending(): Int {
        var changed = 0
        qrEntryRepository.findExpoIdsByPeriodCheck(QrEntryPeriodCheck.PENDING).forEach { expoId ->
            val period =
                try {
                    expoPeriodValidator.period(expoId)
                } catch (e: ExpectedException) {
                    if (e.status == HttpStatus.NOT_FOUND) {
                        val count = markAll(expoId) { _ -> QrEntryPeriodCheck.EXPO_NOT_FOUND }
                        changed += count
                        logger.warn("기간 확인을 기다리던 종이 QR 입장의 박람회가 없습니다: expoId={}, 건수={}", expoId, count)
                    }
                    return@forEach
                }

            var outOfPeriod = 0
            changed +=
                markAll(expoId) { entry ->
                    if (entry.attendanceDate >= period.first && entry.attendanceDate <= period.second) {
                        QrEntryPeriodCheck.VERIFIED
                    } else {
                        outOfPeriod++
                        QrEntryPeriodCheck.OUT_OF_PERIOD
                    }
                }
            if (outOfPeriod > 0) logger.warn("박람회 기간 밖에 기록된 종이 QR 입장이 있습니다: expoId={}, 건수={}", expoId, outOfPeriod)
        }
        return changed
    }

    /** 한 박람회의 대기 입장을 [BATCH_SIZE]건씩 읽어 [statusOf]가 정한 상태로 바꾼다. 바뀐 행은 대기에서 빠지므로 끝까지 돈다. */
    private fun markAll(
        expoId: String,
        statusOf: (PendingQrEntry) -> QrEntryPeriodCheck,
    ): Int {
        var changed = 0
        while (true) {
            val page = qrEntryRepository.findByPeriodCheckAndExpoId(QrEntryPeriodCheck.PENDING, expoId, PageRequest.of(0, BATCH_SIZE))
            if (page.isEmpty()) return changed
            page.groupBy(statusOf).forEach { (status, entries) ->
                changed += qrEntryRepository.updatePeriodCheck(entries.map { it.id }, status)
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
    }
}
