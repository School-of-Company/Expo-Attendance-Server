package team.startup.expo.domain.qr.service.impl

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 박람회 서비스가 응답하지 못해 기간 확인 없이 기록한 종이 QR 입장(`PENDING`)을 다시 확인한다. 입장한 날이 박람회 기간 안이면
 * `VERIFIED`, 밖이면 `OUT_OF_PERIOD`, 박람회가 없으면 `EXPO_NOT_FOUND`로 바꾼다. 입장 기록 자체는 지우지 않는다(운영이 보고 판단).
 * 박람회 서비스가 아직 응답하지 못하면 그 박람회의 입장은 `PENDING`으로 남겨 다음에 다시 시도한다. 토큰 값은 로그에 남기지 않는다.
 */
@Service
class QrEntryPeriodReviewService(
    private val qrEntryRepository: QrEntryRepository,
    private val expoPeriodValidator: ExpoPeriodValidator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 한 번에 최대 [BATCH_SIZE]건을 처리하고 상태를 바꾼 건수를 돌려준다. */
    fun reviewPending(): Int {
        val pending = qrEntryRepository.findByPeriodCheck(QrEntryPeriodCheck.PENDING, PageRequest.of(0, BATCH_SIZE))
        var changed = 0
        pending.groupBy { it.expoId }.forEach { (expoId, entries) ->
            val period =
                try {
                    expoPeriodValidator.period(expoId)
                } catch (e: ExpectedException) {
                    if (e.status == HttpStatus.NOT_FOUND) {
                        changed += qrEntryRepository.updatePeriodCheck(entries.map { it.id }, QrEntryPeriodCheck.EXPO_NOT_FOUND)
                        logger.warn("기간 확인을 기다리던 종이 QR 입장의 박람회가 없습니다: expoId={}, 건수={}", expoId, entries.size)
                    }
                    return@forEach
                }

            val (inPeriod, outOfPeriod) = entries.partition { it.attendanceDate >= period.first && it.attendanceDate <= period.second }
            if (inPeriod.isNotEmpty()) {
                changed += qrEntryRepository.updatePeriodCheck(inPeriod.map { it.id }, QrEntryPeriodCheck.VERIFIED)
            }
            if (outOfPeriod.isNotEmpty()) {
                changed += qrEntryRepository.updatePeriodCheck(outOfPeriod.map { it.id }, QrEntryPeriodCheck.OUT_OF_PERIOD)
                logger.warn("박람회 기간 밖에 기록된 종이 QR 입장이 있습니다: expoId={}, 건수={}", expoId, outOfPeriod.size)
            }
        }
        return changed
    }

    private companion object {
        const val BATCH_SIZE = 200
    }
}
