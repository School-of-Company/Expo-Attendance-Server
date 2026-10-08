package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.presentation.dto.request.AssignPreregisterSessionReqDto
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.service.PreregisterSessionService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

@Service
class PreregisterSessionServiceImpl(
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val clock: Clock,
) : PreregisterSessionService {
    @Transactional
    override fun assign(
        expoId: String,
        participantId: Long,
        reqDto: AssignPreregisterSessionReqDto,
    ) {
        // 삭제와 같은 락을 잡은 뒤에 삭제 기록을 확인해, 삭제된 박람회의 기록이 되살아나지 않게 한다
        deletedExpoRepository.lockExpo(expoId)
        if (deletedExpoRepository.existsById(expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
        }
        preregisterSessionRepository.upsertActive(expoId, participantId, reqDto.sessionId, LocalDateTime.now(clock))
    }

    @Transactional
    override fun cancel(
        expoId: String,
        participantId: Long,
    ) {
        preregisterSessionRepository.cancel(expoId, participantId, LocalDateTime.now(clock))
    }
}
