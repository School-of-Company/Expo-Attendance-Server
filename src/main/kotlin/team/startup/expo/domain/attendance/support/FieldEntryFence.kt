package team.startup.expo.domain.attendance.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * STANDARD 참가자 입장 시도 기록(`tb_entry_scan_fence`, #204). 입장 스캔은 User `POST /internal/entries`를 부르기 전에 시도를 남기고,
 * 결과에 따라 입장으로 확정하거나(200, 확인된 409) 지운다(명확한 400/404). 503·타임아웃·중단이면 남아 PENDING이 된다.
 * 날짜별 현장 방문 취소는 이 기록으로 "User에는 기록됐지만 응답을 못 받은 입장"을 알아내고, PENDING이 있으면 503으로 판단을 미룬다.
 * 시간이 지나도 스스로 지우지 않는다.
 *
 * [begin]과 [finish]가 `REQUIRES_NEW`인 이유: 입장 스캔은 [CancellationGuardGate.scanning]의 트랜잭션(공유 락) 안에서 이 메서드를 부른다.
 * 같은 트랜잭션에 쓰면 User 호출이 실패해 바깥이 롤백될 때 시도 기록도 함께 사라져, User에는 입장이 남았는데 이 서비스는 모르는 상태가 된다.
 * 따로 커밋해 바깥 롤백과 무관하게 남기고, 바깥이 아직 끝나지 않은 동안에도 취소 조회(다른 트랜잭션)가 볼 수 있게 한다.
 * 그 대가로 공유 락을 쥔 스캔이 DB 커넥션을 하나 더 쓴다.
 */
@Service
class FieldEntryFence(
    private val jdbc: JdbcTemplate,
) {
    /** 입장 시도를 PENDING으로 남기고 시도 ID를 돌려준다. 바깥 트랜잭션과 따로 바로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun begin(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO tb_entry_scan_fence (attempt_id, expo_id, participant_id, attendance_date) VALUES (?, ?, ?, ?)",
            id,
            expoId,
            participantId,
            date,
        )
        return id
    }

    /** [entered]면 시도를 입장으로 확정하고, 아니면 지운다. 바깥 트랜잭션과 따로 바로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finish(
        attemptId: UUID,
        entered: Boolean,
    ) {
        if (entered) {
            jdbc.update("UPDATE tb_entry_scan_fence SET entered = TRUE WHERE attempt_id = ?", attemptId)
        } else {
            jdbc.update("DELETE FROM tb_entry_scan_fence WHERE attempt_id = ?", attemptId)
        }
    }

    /** 그 날짜에 입장으로 확정된 시도가 있는지. */
    fun entered(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): Boolean = exists(expoId, participantId, date, true)

    /** 그 날짜에 결과가 아직 불확실한(PENDING) 시도가 있는지. */
    fun pending(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): Boolean = exists(expoId, participantId, date, false)

    private fun exists(
        expoId: String,
        participantId: Long,
        date: LocalDate,
        entered: Boolean,
    ): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tb_entry_scan_fence WHERE expo_id = ? AND participant_id = ? AND attendance_date = ? AND entered = ?)",
            Boolean::class.java,
            expoId,
            participantId,
            date,
            entered,
        ) == true
}
