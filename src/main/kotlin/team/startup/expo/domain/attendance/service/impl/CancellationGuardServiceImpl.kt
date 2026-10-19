package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.AcquireCancellationGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CommitCancellationGuardReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.CancellationGuardService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.AdminCancellationStatusReqDto
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.metrics.AttendanceMetrics
import java.time.Clock
import java.time.LocalDateTime

@Service
class CancellationGuardServiceImpl(
    transactionManager: PlatformTransactionManager,
    private val cancellationGuardRepository: CancellationGuardRepository,
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val standardProgramAttendanceRepository: StandardProgramAttendanceRepository,
    private val trainingProgramAttendanceRepository: TrainingProgramAttendanceRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) : CancellationGuardService {
    private val transaction = TransactionTemplate(transactionManager)

    override fun acquire(
        expoId: String,
        participantId: Long,
        operationId: String,
        reqDto: AcquireCancellationGuardReqDto,
    ): AcquireCancellationGuardResult =
        acquireWith(expoId, participantId, operationId) {
            val status = userClient.getCancellationStatus(CancellationStatusReqDto(expoId, reqDto.phoneNumber, participantId))
            ReadStatus(status.entered, status.linkedTraineeId)
        }

    override fun acquireForAdmin(
        expoId: String,
        participantId: Long,
        operationId: String,
    ): AcquireCancellationGuardResult =
        acquireWith(expoId, participantId, operationId) {
            val status = userClient.getAdminCancellationStatus(AdminCancellationStatusReqDto(expoId, participantId))
            ReadStatus(status.entered, status.linkedTraineeId)
        }

    /** 유저 서비스에서 읽은 취소 판단 재료. 본인 확인 방식(전화번호, 관리자 권한)과 무관하게 둘만 필요하다. */
    private data class ReadStatus(
        val entered: Boolean,
        val linkedTraineeId: Long?,
    )

    private fun acquireWith(
        expoId: String,
        participantId: Long,
        operationId: String,
        readStatus: () -> ReadStatus,
    ): AcquireCancellationGuardResult {
        // 1) 진행 중인 입장·프로그램 스캔이 끝나기를 기다린 뒤 보호를 건다. 이 뒤의 스캔은 거절된다.
        val alreadyCommitted =
            exclusive(expoId, participantId) {
                deletedExpoRepository.lockExpo(expoId)
                if (deletedExpoRepository.existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
                // 이미 중단된 작업의 지연된 acquire가 보호를 다시 걸지 못하게 한다. 새 operationId로 다시 시작해야 한다
                if (cancellationGuardRepository.existsAborted(expoId, participantId, operationId)) {
                    throw ExpectedException(HttpStatus.CONFLICT, "이미 중단된 취소 작업입니다. 새 operationId로 다시 시도해 주세요.")
                }
                val existing = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)
                if (existing == null) {
                    cancellationGuardRepository.insertHeldIfAbsent(expoId, participantId, operationId, LocalDateTime.now(clock))
                } else if (existing.operationId != operationId) {
                    throw ExpectedException(HttpStatus.CONFLICT, "다른 취소 작업이 이미 처리 중이거나 확정되었습니다.")
                }
                existing?.state == CancellationGuardState.COMMITTED
            }
        if (alreadyCommitted) return AcquireCancellationGuardResult.Acquired

        // 2) 입장 여부와 연결된 연수자를 유저 서비스에서 읽는다(트랜잭션 밖). 유저가 모른다고 하면 보호를 풀고 404다.
        val status =
            try {
                userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.")) {
                    readStatus()
                }
            } catch (e: ExpectedException) {
                // 404는 취소할 수 있는 참가자가 아니라는 뜻이라 보호를 풀고, 그 밖의 실패(503)는 보호를 둔 채 같은 작업의 재시도를 기다린다
                if (e.status == HttpStatus.NOT_FOUND) release(expoId, participantId, operationId)
                throw e
            }
        if (status.entered) return blocked(expoId, participantId, operationId, entered = true, programAttended = false)

        // 3) 연결된 연수자의 연수 프로그램 스캔도 막는다. 진행 중인 그 연수자의 스캔이 끝나기를 기다린 뒤 키를 채운다.
        val traineeId = status.linkedTraineeId
        if (traineeId != null) {
            lockTrainee(expoId, traineeId) {
                cancellationGuardRepository.setTrainee(expoId, participantId, operationId, traineeId, LocalDateTime.now(clock))
            }
        }

        // 4) 프로그램 출석이 하나라도 있으면 취소할 수 없다. 보호를 건 뒤에 읽으므로 이 뒤에 새로 생기지 않는다.
        val attended =
            standardProgramAttendanceRepository.existsByParticipantId(participantId) ||
                (traineeId != null && trainingProgramAttendanceRepository.existsByTraineeId(traineeId))
        if (attended) return blocked(expoId, participantId, operationId, entered = false, programAttended = true)
        return AcquireCancellationGuardResult.Acquired
    }

    override fun commit(
        expoId: String,
        participantId: Long,
        operationId: String,
        reqDto: CommitCancellationGuardReqDto?,
    ) {
        exclusive(expoId, participantId) {
            val existing = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)
            if (existing == null || existing.operationId != operationId) {
                throw ExpectedException(HttpStatus.CONFLICT, "이 작업의 취소 보호가 없습니다. 먼저 보호를 걸어야 합니다.")
            }
            if (existing.state == CancellationGuardState.HELD) {
                val revision = reqDto?.revision
                val sessionId = reqDto?.sessionId
                val now = LocalDateTime.now(clock)
                if (sessionId != null) {
                    // 회차 하나의 취소다. 회차 취소 통지보다 먼저 와도 그 회차로는 바로 입장할 수 없게 여기서 취소로 표시한다(같은 revision이라 통지는 멱등이다)
                    preregisterSessionRepository.cancelExistingSession(expoId, participantId, sessionId, revision, now)
                }
                val active =
                    preregisterSessionRepository
                        .findAllByExpoIdAndParticipantId(expoId, participantId)
                        .filter { it.status == PreregisterSessionStatus.ACTIVE }
                // 보호가 걸려 있는 동안 이 취소보다 새로운 신청의 회차 PUT이 먼저 적용되었으면 이 취소는 밀려났다.
                val superseded = revision != null && active.any { it.revision > revision }
                // 회차 하나만 취소했고 확정 상태인 다른 회차가 남아 있으면, 참가자는 남은 회차로 계속 입장·출석할 수 있어야 한다(#160).
                val otherSessionsRemain = sessionId != null && active.any { it.sessionId != sessionId }
                if (superseded || otherSessionsRemain) {
                    // 보호를 확정하지 않고 끝난 작업으로 기록해 입장·출석을 다시 허용한다
                    cancellationGuardRepository.insertAborted(expoId, participantId, operationId, now)
                    cancellationGuardRepository.deleteHeld(expoId, participantId, operationId)
                } else {
                    cancellationGuardRepository.commit(expoId, participantId, operationId, revision, now)
                }
            }
        }
    }

    override fun abort(
        expoId: String,
        participantId: Long,
        operationId: String,
    ) {
        exclusive(expoId, participantId) {
            val existing = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)
            if (existing != null && existing.operationId == operationId && existing.state == CancellationGuardState.COMMITTED) {
                throw ExpectedException(HttpStatus.CONFLICT, "이미 확정된 취소 작업은 중단할 수 없습니다.")
            }
            // 보호를 걸었든 아직 acquire가 도착하지 않았든 이 작업은 끝난 것으로 기록해, 지연된 acquire를 거절하고 같은 abort는 멱등으로 둔다
            cancellationGuardRepository.insertAborted(expoId, participantId, operationId, LocalDateTime.now(clock))
            if (existing != null && existing.operationId == operationId) {
                cancellationGuardRepository.deleteHeld(expoId, participantId, operationId)
            }
        }
    }

    override fun releaseCommitted(
        expoId: String,
        participantId: Long,
        revision: Long?,
    ) {
        exclusive(expoId, participantId) { cancellationGuardRepository.deleteCommitted(expoId, participantId, revision) }
    }

    private fun blocked(
        expoId: String,
        participantId: Long,
        operationId: String,
        entered: Boolean,
        programAttended: Boolean,
    ): AcquireCancellationGuardResult {
        release(expoId, participantId, operationId)
        return AcquireCancellationGuardResult.Blocked(entered, programAttended)
    }

    private fun release(
        expoId: String,
        participantId: Long,
        operationId: String,
    ) = exclusive(expoId, participantId) { cancellationGuardRepository.deleteHeld(expoId, participantId, operationId) }

    private fun <T> exclusive(
        expoId: String,
        participantId: Long,
        block: () -> T,
    ): T = locked(CancellationGuardGate.participantKey(expoId, participantId), block)

    private fun lockTrainee(
        expoId: String,
        traineeId: Long,
        block: () -> Unit,
    ) = locked(CancellationGuardGate.traineeKey(expoId, traineeId), block)

    private fun <T> locked(
        key: String,
        block: () -> T,
    ): T {
        try {
            return requireNotNull(
                transaction.execute {
                    cancellationGuardRepository.setLockTimeout(CancellationGuardGate.LOCK_TIMEOUT)
                    val start = System.nanoTime()
                    try {
                        cancellationGuardRepository.lockExclusive(key)
                    } finally {
                        AttendanceMetrics.recordLockWait("cancel", System.nanoTime() - start)
                    }
                    block()
                },
            )
        } catch (e: CannotAcquireLockException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해 주세요.")
        }
    }
}
