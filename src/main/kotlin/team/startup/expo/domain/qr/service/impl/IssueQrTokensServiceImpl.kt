package team.startup.expo.domain.qr.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.global.exception.ExpectedException
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64

@Service
class IssueQrTokensServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val clock: Clock,
) : IssueQrTokensService {
    private val secureRandom = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    @Transactional
    override fun issue(
        expoId: String,
        reqDto: IssueQrTokensReqDto,
    ): IssueQrTokensResDto {
        // 박람회 ID는 소문자 UUID다. 폼 서비스가 `resolve` 응답의 `expoId`를 UUID 모양으로 검증하므로 다른 값으로 발급하면
        // 설문 단계에서 장애로 처리된다.
        if (!EXPO_ID_PATTERN.matches(expoId)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.")
        }

        // 삭제와 같은 락을 잡은 뒤에 삭제 기록을 확인해, 삭제된 박람회의 토큰이 되살아나지 않게 한다
        deletedExpoRepository.lockExpo(expoId)
        if (deletedExpoRepository.existsById(expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
        }

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
        val EXPO_ID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
