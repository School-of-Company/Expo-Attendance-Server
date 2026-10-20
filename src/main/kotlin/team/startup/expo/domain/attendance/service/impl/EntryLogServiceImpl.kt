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
        // 유저 서비스는 이미 입장을 기록했다. 일시적인 DB 오류로 로그가 빠지면 퇴장 스캔이 "입장 기록 없음"이 되므로 몇 번 다시 시도한다.
        var failure: Exception? = null
        repeat(ATTEMPTS) {
            try {
                val scanner = ScannerName.of(scannerName)
                val inserted =
                    entryLogRepository.insertIfAbsent(
                        expoId,
                        subjectType.name,
                        subjectId,
                        now.toLocalDate(),
                        now,
                        category,
                        scanner,
                    )
                // 퇴장한 뒤 다른 회차로 입장하면 유저 서비스는 새 입장으로 받으므로, 오늘 로그를 다시 열어 재입장으로 남긴다(#174)
                if (inserted == 0) entryLogRepository.reenter(expoId, subjectType.name, subjectId, now.toLocalDate(), now, scanner)
                return
            } catch (e: Exception) {
                failure = e
            }
        }
        // 그래도 안 되면 입장은 그대로 두고 퇴장 기록과 집계에서만 빠진다(식별자는 남기지 않는다)
        logger.warn("입장 로그를 남기지 못했습니다: type={}, 원인={}", subjectType, failure?.javaClass?.simpleName)
    }

    private companion object {
        const val ATTEMPTS = 3
    }
}
