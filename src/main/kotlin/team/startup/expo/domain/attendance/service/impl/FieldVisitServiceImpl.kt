package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.FieldEntryFence
import team.startup.expo.domain.attendance.service.FieldVisitService
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
    private val visits: FieldVisitRepository,
    private val locks: CancellationGuardRepository,
    private val deletedExpos: DeletedExpoRepository,
    private val entries: EntryLogRepository,
    private val fence: FieldEntryFence,
    private val standard: StandardProgramAttendanceRepository,
    private val training: TrainingProgramAttendanceRepository,
    private val user: UserClient,
    @Qualifier("userCircuitBreaker") private val circuit: CircuitBreaker,
) : FieldVisitService {
    private val transaction = TransactionTemplate(transactionManager)

    override fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: ProjectFieldVisitReqDto,
    ) {
        locked(expoId, participantId, registrationId) {
            val old = visits.find(registrationId)
            old?.let { checkIdentity(it, expoId, participantId, request.visitDate) }
            if (old != null && request.generation < old.generation) return@locked
            if (old != null && request.generation == old.generation && old.status == FieldVisitStatus.CANCELLED) return@locked
            if (old != null && request.generation == old.generation && old.operationId == request.operationId &&
                old.status != request.status
            ) {
                throw conflict()
            }
            if (request.status == FieldVisitStatus.CANCELLED && old != null && request.generation == old.generation &&
                visits.guards(registrationId, old.generation).any { it.state == FieldVisitGuardState.HELD }
            ) {
                throw conflict()
            }
            visits.save(
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
            val guards = visits.guards(registrationId, visit.generation)
            val same = guards.find { it.operationId == operationId }
            if (same?.state == FieldVisitGuardState.ABORTED) throw conflict()
            if (guards.any { it.operationId != operationId && it.state != FieldVisitGuardState.ABORTED }) throw conflict()
            val trainee = ownership(expoId, participantId, request.phoneNumber)
            if (same?.state == FieldVisitGuardState.COMMITTED) return@locked
            if (trainee != null) locks.lockExclusive(CancellationGuardGate.traineeKey(expoId, trainee))
            val status = statusOf(visit, trainee)
            if (status.enteredOnVisitDate || status.programAttended || visit.status != FieldVisitStatus.ACTIVE) throw conflict()
            visits.saveGuard(visit, operationId, FieldVisitGuardState.HELD, trainee)
        }
    }

    override fun commit(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    ) = finish(expoId, participantId, registrationId, operationId, request, true)

    override fun abort(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    ) = finish(expoId, participantId, registrationId, operationId, request, false)

    private fun finish(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
        commit: Boolean,
    ) {
        locked(expoId, participantId, registrationId) {
            val visit = target(expoId, participantId, registrationId, request.visitDate)
            if (request.generation < visit.generation) return@locked
            if (request.generation != visit.generation) throw conflict()
            val guard = visits.guards(registrationId, request.generation).find { it.operationId == operationId }
            if (commit) {
                if (guard == null || guard.state == FieldVisitGuardState.ABORTED) throw conflict()
                if (guard.state == FieldVisitGuardState.COMMITTED) return@locked
                visits.save(visit.copy(status = FieldVisitStatus.CANCELLED, operationId = operationId))
                visits.saveGuard(visit, operationId, FieldVisitGuardState.COMMITTED, null)
            } else {
                if (guard?.state == FieldVisitGuardState.COMMITTED) throw conflict()
                visits.saveGuard(visit, operationId, FieldVisitGuardState.ABORTED, null)
            }
        }
    }

    private fun ownership(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
    ): Long? {
        val status =
            circuit.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.")) {
                user.getCancellationStatus(CancellationStatusReqDto(expoId, phoneNumber, participantId))
            }
        if (status.participantId != participantId) throw unavailable()
        return status.linkedTraineeId
    }

    private fun statusOf(
        visit: FieldVisit,
        trainee: Long?,
    ): FieldVisitCancellationResDto {
        val remote =
            circuit.callService("유저") {
                user.getEntryStatus(ParticipantEntryStatusReqDto(visit.expoId, visit.participantId, visit.visitDate))
            }
        if (remote.participantId != visit.participantId || remote.attendanceDate != visit.visitDate) throw unavailable()
        val entered =
            remote.entered || fence.entered(visit.expoId, visit.participantId, visit.visitDate) ||
                entries.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                    visit.expoId,
                    EntrySubjectType.STANDARD,
                    visit.participantId.toString(),
                    visit.visitDate,
                ) != null
        if (!entered && fence.pending(visit.expoId, visit.participantId, visit.visitDate)) throw unavailable()
        val attended =
            standard.existsByParticipantIdAndAttendanceDate(visit.participantId, visit.visitDate) ||
                (trainee != null && training.existsByTraineeIdAndAttendanceDate(trainee, visit.visitDate))
        val guards = visits.guards(visit.registrationId, visit.generation)
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
        val visit = visits.find(registrationId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "등록을 찾지 못 했습니다.")
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

    private fun <T> locked(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        block: () -> T,
    ): T {
        try {
            return requireNotNull(
                transaction.execute {
                    locks.setLockTimeout(CancellationGuardGate.LOCK_TIMEOUT)
                    deletedExpos.lockExpo(expoId)
                    if (deletedExpos.existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
                    locks.lockExclusive(CancellationGuardGate.participantKey(expoId, participantId))
                    locks.lockExclusive("field-visit:$registrationId")
                    block()
                },
            )
        } catch (_: CannotAcquireLockException) {
            throw unavailable()
        }
    }

    private fun conflict() = ExpectedException(HttpStatus.CONFLICT, "등록 또는 취소 작업 상태가 일치하지 않습니다.")

    private fun unavailable() = ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "입장 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.")
}
