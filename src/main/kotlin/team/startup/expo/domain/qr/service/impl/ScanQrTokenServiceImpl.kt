package team.startup.expo.domain.qr.service.impl

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntryStatus
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.support.EntryReplayPolicy
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.attendance.support.ScannerName
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.response.ScanQrTokenResDto
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.ScanQrTokenService
import team.startup.expo.domain.qr.support.QrEntryPeriodReviewService
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 종이 QR 입구 스캔. 일반 입장 스캔처럼 진행 중인 박람회에서만 입장을 기록한다. 다만 종이 QR은 입구 줄을 줄이려고 나눠 주는
 * 것이라, 박람회 서비스가 응답하지 못할 때는 기간 확인 없이 입장을 기록하고 `PENDING`으로 남겨 나중에 다시 확인한다
 * ([QrEntryPeriodReviewService]). 박람회가 없거나(404) 진행 중이 아닌 것이 확인되면 그대로 거부한다.
 *
 * 박람회 서비스를 부르는 동안 DB 트랜잭션을 잡지 않도록 이 서비스에는 `@Transactional`을 두지 않고, 기록은 리포지토리 호출 하나가
 * 각자 트랜잭션이다.
 */
@Service
class ScanQrTokenServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val qrEntryRepository: QrEntryRepository,
    private val entryLogRepository: EntryLogRepository,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val entryReplayPolicy: EntryReplayPolicy,
    private val clock: Clock,
) : ScanQrTokenService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun scan(
        expoId: String,
        reqDto: ScanQrTokenReqDto,
        scannerName: String?,
    ): ScanQrTokenResDto {
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val scanner = ScannerName.of(scannerName)
        val periodCheck = checkPeriod(expoId, today)

        // 같은 날 두 번 스캔해도 한 번만 기록되도록 DB가 판정한다. 0이면 없는 토큰이거나 이미 입장한 것이다.
        // 퇴장 기록과 집계를 위한 입장 로그(토큰 구분 포함)와 입장 이력은 같은 문장에서 함께 남아, 입장과 로그가 따로 어긋나지 않는다(#120)
        if (qrEntryRepository.recordEntry(reqDto.token, expoId, today, now, periodCheck.name, scanner) == 1) {
            if (periodCheck == QrEntryPeriodCheck.PENDING) {
                logger.warn("박람회 기간을 확인하지 못한 채 종이 QR 입장을 기록했습니다(나중에 다시 확인): expoId={}", expoId)
            }
            return ScanQrTokenResDto(EntryStatus.FIRST, now.format(TIME))
        }
        // 오늘 이미 입장했더라도 퇴장한 뒤라면 재입장이다. 퇴장 없이 다시 찍은 것은 재입장이 아니라 거절한다(#148)
        if (qrEntryRepository.reenter(reqDto.token, expoId, today, now, scanner) == 1) {
            return ScanQrTokenResDto(EntryStatus.RE_ENTRY, firstEnteredTime(expoId, reqDto.token, today))
        }

        if (!qrTokenRepository.existsByTokenAndExpoId(reqDto.token, expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "QR 토큰을 찾을 수 없습니다.")
        }
        if (qrTokenRepository.existsByTokenAndExpoIdAndRevokedAtIsNotNull(reqDto.token, expoId)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "사용이 중지된 QR입니다.")
        }
        // 방금 같은 스캐너가 찍은 입장이면 타임아웃 뒤 재시도로 보고 직전 결과를 다시 돌려준다(#176). 아무것도 기록하지 않는다.
        // 없는 토큰과 무효화한 토큰은 위에서 이미 거절했다
        val log =
            entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                expoId,
                EntrySubjectType.PAPER_QR,
                reqDto.token,
                today,
            )
        if (entryReplayPolicy.allows(log, now, scanner)) {
            return ScanQrTokenResDto(EntryStatus.FIRST, log!!.enteredAt.format(TIME))
        }
        throw ExpectedException(HttpStatus.BAD_REQUEST, "오늘 이미 입장한 QR입니다.")
    }

    private fun firstEnteredTime(
        expoId: String,
        token: String,
        today: LocalDate,
    ): String? =
        entryLogRepository
            .findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(expoId, EntrySubjectType.PAPER_QR, token, today)
            ?.enteredAt
            ?.format(TIME)

    /** 박람회 서비스가 응답하지 못한 경우(503)에만 확인을 미루고, 없음(404)이나 기간 밖(400)은 그대로 던진다. */
    private fun checkPeriod(
        expoId: String,
        today: LocalDate,
    ): QrEntryPeriodCheck =
        try {
            expoPeriodValidator.checkInProgress(expoId, today)
            QrEntryPeriodCheck.VERIFIED
        } catch (e: ExpectedException) {
            if (e.status != HttpStatus.SERVICE_UNAVAILABLE) throw e
            QrEntryPeriodCheck.PENDING
        }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
