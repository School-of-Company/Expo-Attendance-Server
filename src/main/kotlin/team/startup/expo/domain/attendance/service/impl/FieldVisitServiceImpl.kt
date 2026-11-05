package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitCancellationResDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitGuardState
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.repository.FieldVisitGuard
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.FieldVisitService
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.attendance.support.GuardLockExecutor
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.ParticipantEntryStatusReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.util.UUID

@Service
class FieldVisitServiceImpl(
    transactionManager: PlatformTransactionManager,
    private val fieldVisitRepository: FieldVisitRepository,
    private val cancellationGuardRepository: CancellationGuardRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val entryLogRepository: EntryLogRepository,
    private val fieldEntryFence: FieldEntryFence,
    private val standardProgramAttendanceRepository: StandardProgramAttendanceRepository,
    private val trainingProgramAttendanceRepository: TrainingProgramAttendanceRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
) : FieldVisitService {
    private val lockExecutor = GuardLockExecutor(transactionManager, cancellationGuardRepository)

    override fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: ProjectFieldVisitReqDto,
    ) {
        locked(expoId, participantId, registrationId) {
            val old = fieldVisitRepository.find(registrationId)
            if (old != null) {
                checkIdentity(old, expoId, participantId, request.visitDate)
                // 이전 세대 요청은 아무것도 바꾸지 않는다
                if (request.generation < old.generation) return@locked
                if (request.generation == old.generation) {
                    // 같은 세대의 CANCELLED는 최종 상태다
                    if (old.status == FieldVisitStatus.CANCELLED) return@locked
                    // 같은 작업이 다른 상태를 보내면 충돌이다
                    if (old.operationId == request.operationId && old.status != request.status) throw conflict()
                    // 취소 보호가 HELD인 동안 온 취소 projection은 commit보다 먼저 온 것이라 충돌이다(acquire → commit → projection 순서)
                    if (request.status == FieldVisitStatus.CANCELLED &&
                        fieldVisitRepository.guards(registrationId, old.generation).any { it.state == FieldVisitGuardState.HELD }
                    ) {
                        throw conflict()
                    }
                }
            }
            fieldVisitRepository.save(
                FieldVisit(
                    registrationId,
                    expoId,
                    participantId,
                    request.visitDate,
                    request.generation,
                    request.status,
                    request.operationId,
                ),
            )
        }
    }

    override fun status(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: FieldVisitCancellationReqDto,
    ): FieldVisitCancellationResDto =
        locked(expoId, participantId, registrationId) {
            val visit = target(expoId, participantId, registrationId, request.visitDate)
            if (request.generation != visit.generation) throw conflict()
            val trainee = ownership(expoId, participantId, request.phoneNumber)
            statusOf(visit, trainee)
        }

    override fun acquire(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FieldVisitCancellationReqDto,
    ) {
        locked(expoId, participantId, registrationId) {
            val visit = target(expoId, participantId, registrationId, request.visitDate)
            if (request.generation < visit.generation) return@locked
            if (request.generation != visit.generation) throw conflict()
            val guards = fieldVisitRepository.guards(registrationId, visit.generation)
            val same = guards.find { it.operationId == operationId }
            if (same?.state == FieldVisitGuardState.ABORTED) throw conflict()
            if (guards.any { it.operationId != operationId && it.state != FieldVisitGuardState.ABORTED }) throw conflict()
            val trainee = ownership(expoId, participantId, request.phoneNumber)
            if (same?.state == FieldVisitGuardState.COMMITTED) return@locked
            if (trainee != null) lockExecutor.lockExclusive(CancellationGuardGate.traineeKey(expoId, trainee))
            val status = statusOf(visit, trainee)
            if (status.enteredOnVisitDate || status.programAttended || visit.status != FieldVisitStatus.ACTIVE) throw conflict()
            fieldVisitRepository.saveGuard(visit, operationId, FieldVisitGuardState.HELD, trainee)
        }
    }

    override fun commit(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    ) = finishGuard(expoId, participantId, registrationId, operationId, request) { visit, guard ->
        if (guard == null || guard.state == FieldVisitGuardState.ABORTED) throw conflict()
        if (guard.state == FieldVisitGuardState.COMMITTED) return@finishGuard
        fieldVisitRepository.save(visit.copy(status = FieldVisitStatus.CANCELLED, operationId = operationId))
        fieldVisitRepository.saveGuard(visit, operationId, FieldVisitGuardState.COMMITTED, null)
    }

    override fun abort(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    ) = finishGuard(expoId, participantId, registrationId, operationId, request) { visit, guard ->
        if (guard?.state == FieldVisitGuardState.COMMITTED) throw conflict()
        fieldVisitRepository.saveGuard(visit, operationId, FieldVisitGuardState.ABORTED, null)
    }

    /**
     * commit·abort의 공통 앞부분이다. 등록을 확인하고, 이전 세대 요청이면 아무것도 하지 않으며, 같은 세대면 이 작업의 보호(없으면 null)를
     * [finish]에 넘긴다.
     */
    private fun finishGuard(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
        finish: (FieldVisit, FieldVisitGuard?) -> Unit,
    ) {
        locked(expoId, participantId, registrationId) {
            val visit = target(expoId, participantId, registrationId, request.visitDate)
            if (request.generation < visit.generation) return@locked
            if (request.generation != visit.generation) throw conflict()
            finish(visit, fieldVisitRepository.guards(registrationId, request.generation).find { it.operationId == operationId })
        }
    }

    private fun ownership(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
    ): Long? {
        val status =
            userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.")) {
                userClient.getCancellationStatus(CancellationStatusReqDto(expoId, phoneNumber, participantId))
            }
        if (status.participantId != participantId) throw unavailable()
        return status.linkedTraineeId
    }

    private fun statusOf(
        visit: FieldVisit,
        trainee: Long?,
    ): FieldVisitCancellationResDto {
        val remote =
            userCircuitBreaker.callService("유저") {
                userClient.getEntryStatus(ParticipantEntryStatusReqDto(visit.expoId, visit.participantId, visit.visitDate))
            }
        if (remote.participantId != visit.participantId || remote.attendanceDate != visit.visitDate) throw unavailable()
        val entered =
            remote.entered || fieldEntryFence.entered(visit.expoId, visit.participantId, visit.visitDate) ||
                entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                    visit.expoId,
                    EntrySubjectType.STANDARD,
                    visit.participantId.toString(),
                    visit.visitDate,
                ) != null
        if (!entered && fieldEntryFence.pending(visit.expoId, visit.participantId, visit.visitDate)) throw unavailable()
        val attended =
            standardProgramAttendanceRepository.existsByParticipantIdAndAttendanceDate(visit.participantId, visit.visitDate) ||
                (trainee != null && trainingProgramAttendanceRepository.existsByTraineeIdAndAttendanceDate(trainee, visit.visitDate))
        val guards = fieldVisitRepository.guards(visit.registrationId, visit.generation)
        val state =
            when {
                guards.any { it.state == FieldVisitGuardState.HELD } -> FieldVisitGuardState.HELD
                guards.any { it.state == FieldVisitGuardState.COMMITTED } -> FieldVisitGuardState.COMMITTED
                else -> FieldVisitGuardState.NONE
            }
        return FieldVisitCancellationResDto(
            visit.registrationId,
            visit.participantId,
            visit.visitDate,
            visit.generation,
            entered,
            attended,
            state,
            trainee,
        )
    }

    private fun target(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        date: LocalDate,
    ): FieldVisit {
        val visit = fieldVisitRepository.find(registrationId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "등록을 찾지 못 했습니다.")
        checkIdentity(visit, expoId, participantId, date)
        return visit
    }

    private fun checkIdentity(
        visit: FieldVisit,
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ) {
        if (visit.expoId != expoId || visit.participantId != participantId || visit.visitDate != date) throw conflict()
    }

    /** 박람회 락 → 박람회 삭제 확인 → 참가자 배타 락 → 등록 ID 배타 락 순서다(#167). 락 대기 지표는 기록하지 않는다. */
    private fun <T> locked(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        block: () -> T,
    ): T =
        lockExecutor.execute(UNAVAILABLE) {
            deletedExpoRepository.lockExpoShared(expoId)
            if (deletedExpoRepository.existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
            lockExecutor.lockExclusive(CancellationGuardGate.participantKey(expoId, participantId))
            lockExecutor.lockExclusive(CancellationGuardGate.fieldVisitKey(registrationId))
            block()
        }

    private fun conflict() = ExpectedException(HttpStatus.CONFLICT, "등록 또는 취소 작업 상태가 일치하지 않습니다.")

    private fun unavailable() = ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE)

    companion object {
        private const val UNAVAILABLE = "입장 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."
    }
}
