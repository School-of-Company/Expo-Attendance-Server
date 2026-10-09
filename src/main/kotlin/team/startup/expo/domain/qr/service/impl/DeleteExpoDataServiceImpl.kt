package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import java.time.Clock
import java.time.LocalDateTime

/**
 * 박람회가 삭제되면 그 박람회에 묶인 종이 QR 토큰(입장 기록 포함)과 사전등록 회차 기록을 지우고 삭제 기록을 남긴다.
 * 삭제 기록은 발급이 지운 데이터를 되살리지 못하게 막는다. 발급과 같은 락을 잡아 두 요청을 한 줄로 세우므로
 * 삭제와 겹친 발급은 삭제 전에 끝나 함께 지워지거나 삭제 뒤에 거부된다. 여러 번 불러도 결과는 같다.
 */
@Service
class DeleteExpoDataServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val clock: Clock,
) : DeleteExpoDataService {
    @Transactional
    override fun delete(expoId: String) {
        deletedExpoRepository.lockExpo(expoId)
        deletedExpoRepository.insertIfAbsent(expoId, LocalDateTime.now(clock))
        qrTokenRepository.deleteByExpoId(expoId)
        preregisterSessionRepository.deleteByExpoId(expoId)
    }
}
