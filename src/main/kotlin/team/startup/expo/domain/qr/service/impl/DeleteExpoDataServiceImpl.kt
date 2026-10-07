package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.DeleteExpoDataService

/** 박람회가 삭제되면 그 박람회에 묶인 종이 QR 토큰(입장 기록 포함)과 입장 이벤트를 지운다. */
@Service
class DeleteExpoDataServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val entryOutboxRepository: EntryOutboxRepository,
) : DeleteExpoDataService {
    @Transactional
    override fun delete(expoId: String) {
        qrTokenRepository.deleteByExpoId(expoId)
        entryOutboxRepository.deleteByExpoId(expoId)
    }
}
