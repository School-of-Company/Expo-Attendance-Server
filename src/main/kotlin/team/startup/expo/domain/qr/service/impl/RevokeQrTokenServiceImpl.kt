package team.startup.expo.domain.qr.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrTokenCode
import team.startup.expo.domain.qr.presentation.dto.request.RevokeQrTokenReqDto
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.RevokeQrTokenService
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

/** 분실한 종이 QR을 코드로 무효화한다. 이미 무효화한 코드를 다시 보내도 같은 결과다(시각은 처음 그대로). */
@Service
class RevokeQrTokenServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val clock: Clock,
) : RevokeQrTokenService {
    @Transactional
    override fun revoke(
        expoId: String,
        reqDto: RevokeQrTokenReqDto,
    ) {
        val code = QrTokenCode.normalize(reqDto.code) ?: throw ExpectedException(HttpStatus.BAD_REQUEST, "QR 코드 형식이나 확인 문자가 올바르지 않습니다.")
        if (qrTokenRepository.revokeByCode(expoId, code, LocalDateTime.now(clock)) == 0) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "QR 코드를 찾을 수 없습니다.")
        }
    }
}
