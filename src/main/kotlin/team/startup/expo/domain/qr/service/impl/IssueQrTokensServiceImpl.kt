package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.IssueQrTokensService
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64

@Service
class IssueQrTokensServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val clock: Clock,
) : IssueQrTokensService {
    private val secureRandom = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    @Transactional
    override fun issue(
        expoId: String,
        reqDto: IssueQrTokensReqDto,
    ): IssueQrTokensResDto {
        val now = LocalDateTime.now(clock)
        val tokens = List(reqDto.count) { generateToken() }

        qrTokenRepository.saveAll(
            tokens.map { QrToken(token = it, expoId = expoId, category = reqDto.category, createdAt = now) },
        )

        return IssueQrTokensResDto(tokens)
    }

    /** 순번처럼 예측할 수 있으면 남의 QR로 입장·응답할 수 있어서 16바이트 난수를 쓴다(22자). */
    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return base64Url.encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES = 16
    }
}
