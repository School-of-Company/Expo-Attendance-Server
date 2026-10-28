package team.startup.expo.domain.qr.support

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
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
    private val qrTokenRepository: QrTokenRepository,
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

    /** 현장 종이 QR 코드(#243)가 규칙 안이면 이 박람회의 토큰으로 등록하고 저장 토큰을 돌려준다. 규칙 밖이면 입력 그대로 돌려준다. */
    @Transactional
    fun registerFieldToken(
        expoId: String,
        input: String,
        now: LocalDateTime,
    ): String {
        val field = FieldQrCode.parse(input) ?: return input
        val token = field.token(expoId)
        qrTokenRepository.registerField(token, expoId, field.category.name, field.code, now)
        return token
    }
}
