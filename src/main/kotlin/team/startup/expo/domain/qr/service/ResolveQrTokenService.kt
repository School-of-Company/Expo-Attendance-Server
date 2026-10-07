package team.startup.expo.domain.qr.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.presentation.dto.ResolveQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.ResolveQrTokenResDto
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 폼 서비스가 응답자의 QR 토큰이 "입장이 확인된 토큰인가"를 물을 때 쓴다. 없는 토큰과 입장 전 토큰을 구분하면
 * 토큰의 존재 여부가 드러나므로 둘 다 404로 답한다. 토큰 값은 오류 메시지와 로그에 남기지 않는다.
 */
@Service
class ResolveQrTokenService(
    private val qrTokenRepository: QrTokenRepository,
) {
    @Transactional(readOnly = true)
    fun execute(reqDto: ResolveQrTokenReqDto): ResolveQrTokenResDto {
        val expoId =
            qrTokenRepository.findEnteredExpoId(reqDto.token)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "입장이 확인된 QR 토큰이 아닙니다.")
        return ResolveQrTokenResDto(expoId)
    }
}
