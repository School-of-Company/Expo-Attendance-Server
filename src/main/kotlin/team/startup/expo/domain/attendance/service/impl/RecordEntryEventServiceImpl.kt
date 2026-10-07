package team.startup.expo.domain.attendance.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.attendance.service.RecordEntryEventService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 박람회 삭제와 입장 스캔이 겹쳐도 삭제된 박람회의 이벤트(전화번호 포함)가 다시 생기지 않게 한다. 삭제와 같은
 * 박람회별 락을 잡은 뒤 삭제 기록을 확인하고, 락을 쥔 채 같은 트랜잭션에서 저장하므로 삭제가 먼저 커밋됐다면
 * 저장하지 않고, 저장이 먼저였다면 삭제가 그 행을 함께 지운다.
 */
@Service
class RecordEntryEventServiceImpl(
    private val deletedExpoRepository: DeletedExpoRepository,
    private val entryOutboxRepository: EntryOutboxRepository,
    private val clock: Clock,
) : RecordEntryEventService {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun record(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
        attendanceDate: LocalDate,
    ): Boolean {
        deletedExpoRepository.lockExpo(expoId)
        if (deletedExpoRepository.existsById(expoId)) {
            logger.info("삭제된 박람회라 입장 이벤트를 남기지 않습니다: expoId={}", expoId)
            return false
        }

        return entryOutboxRepository.insertIfAbsent(
            eventId = UUID.randomUUID(),
            expoId = expoId,
            participantId = participantId,
            phoneNumber = phoneNumber,
            attendanceDate = attendanceDate,
            createdAt = LocalDateTime.now(clock),
        ) == 1
    }
}
