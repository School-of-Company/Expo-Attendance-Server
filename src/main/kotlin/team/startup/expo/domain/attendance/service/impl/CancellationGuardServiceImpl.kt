package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.presentation.dto.request.AcquireCancellationGuardReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.AcquireCancellationGuardResult
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.CancellationGuardService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

@Service
class CancellationGuardServiceImpl(
    transactionManager: PlatformTransactionManager,
    private val cancellationGuardRepository: CancellationGuardRepository,
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
    ): AcquireCancellationGuardResult {
        // 1) 진행 중인 입장·프로그램 스캔이 끝나기를 기다린 뒤 보호를 건다. 이 뒤의 스캔은 거절된다.
        val alreadyCommitted =
            exclusive(expoId, participantId) {
                deletedExpoRepository.lockExpo(expoId)
                if (deletedExpoRepository.existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
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
                    userClient.getCancellationStatus(CancellationStatusReqDto(expoId, reqDto.phoneNumber, participantId))
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
    ) {
        exclusive(expoId, participantId) {
            val existing = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)
            if (existing == null || existing.operationId != operationId) {
                throw ExpectedException(HttpStatus.CONFLICT, "이 작업의 취소 보호가 없습니다. 먼저 보호를 걸어야 합니다.")
            }
            if (existing.state == CancellationGuardState.HELD) {
                cancellationGuardRepository.commit(expoId, participantId, operationId, LocalDateTime.now(clock))
            }
        }
    }

    override fun abort(
        expoId: String,
        participantId: Long,
        operationId: String,
    ) {
        exclusive(expoId, participantId) {
            val existing = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId) ?: return@exclusive
            if (existing.operationId != operationId) return@exclusive
            if (existing.state == CancellationGuardState.COMMITTED) {
                throw ExpectedException(HttpStatus.CONFLICT, "이미 확정된 취소 작업은 중단할 수 없습니다.")
            }
            cancellationGuardRepository.deleteHeld(expoId, participantId, operationId)
        }
    }

    override fun releaseCommitted(
        expoId: String,
        participantId: Long,
    ) {
        exclusive(expoId, participantId) { cancellationGuardRepository.deleteCommitted(expoId, participantId) }
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
                    cancellationGuardRepository.lockExclusive(key)
                    block()
                },
            )
        } catch (e: CannotAcquireLockException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "진행 중인 스캔이 끝나지 않아 잠시 후 다시 시도해 주세요.")
        }
    }
}
