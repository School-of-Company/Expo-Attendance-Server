package team.startup.expo.domain.qr.support

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.repository.QrEntryRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 종이 QR 입장 기록의 쓰기 한 문장씩을 각자 트랜잭션으로 감싼다. 종이 QR 스캔은 박람회 서비스를 부르는 동안 DB 트랜잭션을 잡지 않으려고
 * `@Transactional`을 두지 않으므로, 쓰기만 이 빈을 거쳐 짧은 트랜잭션으로 한다. 같은 클래스 안에서 부르면 프록시를 타지 않으므로
 * 서비스와 별도의 빈이어야 한다.
 */
@Component
class QrEntryWriter(
    private val qrEntryRepository: QrEntryRepository,
) {
    @Transactional
    fun recordEntry(
        token: String,
        expoId: String,
        attendanceDate: LocalDate,
        enteredAt: LocalDateTime,
        periodCheck: String,
        scanner: String?,
    ): Int = qrEntryRepository.recordEntry(token, expoId, attendanceDate, enteredAt, periodCheck, scanner)

    @Transactional
    fun reenter(
        token: String,
        expoId: String,
        attendanceDate: LocalDate,
        occurredAt: LocalDateTime,
        scanner: String?,
    ): Int = qrEntryRepository.reenter(token, expoId, attendanceDate, occurredAt, scanner)
}
