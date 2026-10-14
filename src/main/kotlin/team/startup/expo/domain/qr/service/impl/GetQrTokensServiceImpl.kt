package team.startup.expo.domain.qr.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.presentation.dto.request.GetQrTokensReqDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenItemResDto
import team.startup.expo.domain.qr.presentation.dto.response.QrTokenListResDto
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.GetQrTokensService

@Service
class GetQrTokensServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
) : GetQrTokensService {
    @Transactional(readOnly = true)
    override fun get(
        expoId: String,
        reqDto: GetQrTokensReqDto,
    ): QrTokenListResDto =
        QrTokenListResDto(
            qrTokenRepository
                .findByRange(expoId, reqDto.category, reqDto.fromSerial, PageRequest.of(0, reqDto.count))
                .map { QrTokenItemResDto.from(it) },
        )
}
