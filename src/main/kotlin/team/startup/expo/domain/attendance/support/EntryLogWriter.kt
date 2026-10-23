package team.startup.expo.domain.attendance.support

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 입장 로그의 쓰기 한 문장씩을 각자 트랜잭션으로 감싼다. 입장·퇴장 스캔 서비스는 다른 서비스를 부르는 동안 DB 트랜잭션을 잡지 않으려고
 * `@Transactional`을 두지 않으므로, 쓰기만 이 빈을 거쳐 짧은 트랜잭션으로 한다. 이미 트랜잭션 안(취소 보호 관문)에서 부르면 그 트랜잭션에 합류한다.
 * 같은 클래스 안에서 부르면 프록시를 타지 않으므로 서비스와 별도의 빈이어야 한다.
 */
@Component
class EntryLogWriter(
    private val entryLogRepository: EntryLogRepository,
) {
    @Transactional
    fun insertIfAbsent(
        expoId: String,
        subjectType: String,
        subjectId: String,
        attendanceDate: LocalDate,
        enteredAt: LocalDateTime,
        category: String?,
        scanner: String?,
    ): Int = entryLogRepository.insertIfAbsent(expoId, subjectType, subjectId, attendanceDate, enteredAt, category, scanner)

    @Transactional
    fun reenter(
        expoId: String,
        subjectType: String,
        subjectId: String,
        attendanceDate: LocalDate,
        occurredAt: LocalDateTime,
        scanner: String?,
    ): Int = entryLogRepository.reenter(expoId, subjectType, subjectId, attendanceDate, occurredAt, scanner)

    @Transactional
    fun markExited(
        expoId: String,
        subjectType: String,
        subjectId: String,
        attendanceDate: LocalDate,
        exitedAt: LocalDateTime,
        exitScanner: String?,
    ): Int = entryLogRepository.markExited(expoId, subjectType, subjectId, attendanceDate, exitedAt, exitScanner)
}
