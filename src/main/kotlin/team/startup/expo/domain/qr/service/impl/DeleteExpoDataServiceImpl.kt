package team.startup.expo.domain.qr.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.repository.WalkInLogRepository
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.domain.qr.service.DeleteExpoDataService
import java.time.Clock
import java.time.LocalDateTime

/**
 * 박람회가 삭제되면 삭제 기록(`tb_deleted_expo`)을 남기고 그 박람회의 다음 표를 지운다: `tb_qr_token`(`tb_qr_entry`는 외래키
 * `ON DELETE CASCADE`로 함께), `tb_preregister_session`, `tb_cancellation_guard`, `tb_cancellation_aborted`, `tb_entry_event`,
 * `tb_entry_log`, `tb_walk_in_log`, `tb_field_visit_guard`, `tb_field_visit`, `tb_entry_scan_fence`.
 * 삭제 기록은 발급이 지운 데이터를 되살리지 못하게 막는다. 발급과 같은 락을 잡아 두 요청을 한 줄로 세우므로
 * 삭제와 겹친 발급은 삭제 전에 끝나 함께 지워지거나 삭제 뒤에 거부된다. 여러 번 불러도 결과는 같다.
 */
@Service
class DeleteExpoDataServiceImpl(
    private val qrTokenRepository: QrTokenRepository,
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val cancellationGuardRepository: CancellationGuardRepository,
    private val entryLogRepository: EntryLogRepository,
    private val walkInLogRepository: WalkInLogRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val clock: Clock,
    private val fieldVisits: FieldVisitRepository,
) : DeleteExpoDataService {
    @Transactional
    override fun delete(expoId: String) {
        deletedExpoRepository.lockExpo(expoId)
        deletedExpoRepository.insertIfAbsent(expoId, LocalDateTime.now(clock))
        qrTokenRepository.deleteByExpoId(expoId)
        preregisterSessionRepository.deleteByExpoId(expoId)
        cancellationGuardRepository.deleteByExpoId(expoId)
        cancellationGuardRepository.deleteAbortedByExpoId(expoId)
        entryLogRepository.deleteEventsByExpoId(expoId)
        entryLogRepository.deleteByExpoId(expoId)
        walkInLogRepository.deleteByExpoId(expoId)
        fieldVisits.deleteExpo(expoId)
    }
}
