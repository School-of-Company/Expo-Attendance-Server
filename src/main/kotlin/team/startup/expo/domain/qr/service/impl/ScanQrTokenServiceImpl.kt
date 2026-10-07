package team.startup.expo.domain.qr.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.ScanQrTokenService
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

@Service
class ScanQrTokenServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val qrEntryRepository: QrEntryRepository,
    private val clock: Clock,
) : ScanQrTokenService {
    @Transactional
    override fun scan(
        expoId: String,
        reqDto: ScanQrTokenReqDto,
    ) {
        val now = LocalDateTime.now(clock)

        // 같은 날 두 번 스캔해도 한 번만 기록되도록 DB가 판정한다. 0이면 없는 토큰이거나 이미 입장한 것이다.
        if (qrEntryRepository.insertIfAbsent(reqDto.token, expoId, now.toLocalDate(), now) == 1) return

        if (!qrTokenRepository.existsByTokenAndExpoId(reqDto.token, expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "QR 토큰을 찾을 수 없습니다.")
        }
        throw ExpectedException(HttpStatus.BAD_REQUEST, "오늘 이미 입장한 QR입니다.")
    }
}
