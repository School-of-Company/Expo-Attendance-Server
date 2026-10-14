package team.startup.expo.domain.attendance.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryLogService
import team.startup.expo.domain.attendance.service.ScannerName
import java.time.Clock
import java.time.LocalDateTime

@Service
class EntryLogServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val clock: Clock,
) : EntryLogService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordEntry(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: String,
        category: String?,
        scannerName: String?,
    ) {
        val now = LocalDateTime.now(clock)
        try {
            entryLogRepository.insertIfAbsent(
                expoId,
                subjectType.name,
                subjectId,
                now.toLocalDate(),
                now,
                category,
                ScannerName.of(scannerName),
            )
        } catch (e: Exception) {
            // 입장은 이미 기록되었다. 로그가 빠지면 퇴장 기록과 집계에서만 빠지므로 입장을 실패시키지 않는다(식별자는 남기지 않는다)
            logger.warn("입장 로그를 남기지 못했습니다: type={}, 원인={}", subjectType, e.javaClass.simpleName)
        }
    }
}
