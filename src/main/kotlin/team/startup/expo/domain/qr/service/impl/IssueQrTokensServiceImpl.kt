package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.support.ExpoIdFormat
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.entity.QrTokenCode
import team.startup.expo.domain.qr.presentation.dto.request.IssueQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.IssueQrTokensResDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenItemResDto
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.IssueQrTokensService
import team.startup.expo.domain.qr.support.lockAndRequireAlive
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
        // 박람회 ID 형식(소문자 UUID)을 다른 관리 API와 같은 규칙으로 맞춘다
        ExpoIdFormat.requireValid(expoId)

        // 삭제와 같은 락을 잡은 뒤에 삭제 기록을 확인해, 삭제된 박람회의 토큰이 되살아나지 않게 한다
        deletedExpoRepository.lockAndRequireAlive(expoId)
        // 박람회 락은 공유라서 연번은 박람회·구분 락으로 따로 세운다(#248)
        qrTokenRepository.lockSerial("qr-serial:$expoId:${reqDto.category}")

        val now = LocalDateTime.now(clock)
        // 같은 연번 락 안이라 구분별 연번이 겹치지 않는다. 무효화한 토큰의 연번도 다시 쓰지 않는다.
        val firstSerial = qrTokenRepository.findMaxSerial(expoId, reqDto.category) + 1
        val issued =
            List(reqDto.count) { index ->
                val serial = firstSerial + index
                QrToken(
                    token = generateToken(),
                    expoId = expoId,
                    category = reqDto.category,
                    createdAt = now,
                    serial = serial,
                    code = QrTokenCode.of(reqDto.category, serial),
                )
            }

        qrTokenRepository.saveAll(issued)

        return IssueQrTokensResDto(
            tokens = issued.map { it.token },
            items = issued.map { QrTokenItemResDto.from(it) },
        )
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
