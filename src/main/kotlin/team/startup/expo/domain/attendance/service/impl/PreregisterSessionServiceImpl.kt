package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.AssignPreregisterSessionReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CancelPreregisterSessionReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FieldRegisterPreregisterSessionReqDto
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.service.CancellationGuardService
import team.startup.expo.domain.attendance.service.PreregisterSessionService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

@Service
class PreregisterSessionServiceImpl(
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val cancellationGuardService: CancellationGuardService,
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
        val now = LocalDateTime.now(clock)
        val sessionId = reqDto.sessionId
        val revision = reqDto.revision
        if (revision == null) {
            // 재신청으로 회차가 다시 기록되면 확정된 취소 보호를 지워 다시 입장할 수 있게 한다
            cancellationGuardService.releaseCommitted(expoId, participantId, null)
            preregisterSessionRepository.upsertActive(expoId, participantId, sessionId, now)
            return
        }
        val applied = preregisterSessionRepository.upsertActiveIfNewer(expoId, participantId, sessionId, revision, now)
        if (applied == 0) {
            // 오래된 요청은 상태를 바꾸지 않으므로 취소 보호도 풀지 않는다
            val sameRevision = sameRevision(expoId, participantId, revision)
            val conflict =
                sameRevision.any {
                    it.sessionId == null || (it.sessionId == sessionId && it.status != PreregisterSessionStatus.ACTIVE)
                }
            if (conflict) throw revisionConflict()
        } else {
            cancellationGuardService.releaseCommitted(expoId, participantId, revision)
        }
    }

    @Transactional
    override fun fieldRegister(
        expoId: String,
        participantId: Long,
        reqDto: FieldRegisterPreregisterSessionReqDto,
    ) {
        deletedExpoRepository.lockExpo(expoId)
        if (deletedExpoRepository.existsById(expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
        }
        preregisterSessionRepository.fieldRegisterIfNewer(expoId, participantId, reqDto.revision, LocalDateTime.now(clock))
        cancellationGuardService.releaseCommitted(expoId, participantId, reqDto.revision)
    }

    @Transactional
    override fun cancel(
        expoId: String,
        participantId: Long,
        reqDto: CancelPreregisterSessionReqDto,
    ) {
        val now = LocalDateTime.now(clock)
        val sessionId = reqDto.sessionId
        val revision = reqDto.revision
        if (revision == null) {
            if (sessionId == null) {
                preregisterSessionRepository.cancelAll(expoId, participantId, now)
            } else {
                preregisterSessionRepository.cancelExistingSession(expoId, participantId, sessionId, null, now)
            }
            return
        }
        // 취소 기록을 새로 만들 수 있으므로, 삭제된 박람회의 기록이 되살아나지 않게 assign과 같은 락과 확인을 거친다
        deletedExpoRepository.lockExpo(expoId)
        if (deletedExpoRepository.existsById(expoId)) return
        if (sessionId != null) {
            val applied = preregisterSessionRepository.cancelSessionIfNewer(expoId, participantId, sessionId, revision, now)
            if (applied == 0 &&
                sameRevision(expoId, participantId, revision).any {
                    it.sessionId == sessionId && it.status != PreregisterSessionStatus.CANCELLED
                }
            ) {
                throw revisionConflict()
            }
            return
        }
        preregisterSessionRepository.cancelAllSessionsIfNewer(expoId, participantId, revision, now)
        preregisterSessionRepository.recordCancelWithoutSessionIfNewer(expoId, participantId, revision, now)
        // 같은 revision으로 이미 확정된 회차가 남아 있으면 같은 revision에 다른 내용이다
        if (sameRevision(expoId, participantId, revision).any { it.status == PreregisterSessionStatus.ACTIVE }) {
            throw revisionConflict()
        }
    }

    private fun sameRevision(
        expoId: String,
        participantId: Long,
        revision: Long,
    ) = preregisterSessionRepository.findAllByExpoIdAndParticipantId(expoId, participantId).filter { it.revision == revision }

    private fun revisionConflict() = ExpectedException(HttpStatus.CONFLICT, "같은 revision에 다른 내용의 요청이 이미 반영되었습니다.")
}
