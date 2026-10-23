package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResult
import team.startup.expo.domain.attendance.service.ScanTrainingProgramService
import team.startup.expo.domain.attendance.support.AbstractProgramScanService
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.attendance.support.ProgramAttendanceResult
import team.startup.expo.domain.attendance.support.ProgramScanNameResolver
import team.startup.expo.domain.attendance.support.RecordProgramAttendanceService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.user.ResolveTraineeByParticipantReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock

/** v1 `ScanTrainingProByQrCodeServiceImpl`의 연수 프로그램 출석. 동작은 일반 프로그램과 같다. */
@Service
class ScanTrainingProgramServiceImpl(
    private val userClient: UserClient,
    private val expoClient: ExpoClient,
    private val applicationClient: ApplicationClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    @Qualifier("expoCircuitBreaker") expoCircuitBreaker: CircuitBreaker,
    @Qualifier("applicationCircuitBreaker") applicationCircuitBreaker: CircuitBreaker,
    expoPeriodValidator: ExpoPeriodValidator,
    recordProgramAttendanceService: RecordProgramAttendanceService,
    cancellationGuardGate: CancellationGuardGate,
    private val programScanNameResolver: ProgramScanNameResolver,
    clock: Clock,
) : AbstractProgramScanService<Unit>(
        expoCircuitBreaker,
        applicationCircuitBreaker,
        expoPeriodValidator,
        recordProgramAttendanceService,
        cancellationGuardGate,
        clock,
    ),
    ScanTrainingProgramService {
    override val programType = ProgramType.TRAINING
    override val programNotFoundMessage = "연수 프로그램을 찾지 못했습니다."
    override val notAppliedMessage = "연수 프로그램을 참가 중인 유저를 찾지 못 했습니다."

    override fun scan(
        programId: Long,
        reqDto: ScanTrainingProgramReqDto,
    ): ProgramScanResDto {
        val traineeId = resolveTraineeId(reqDto)
        // 연수 프로그램은 시간대 검사를 하지 않아 입실이 거부되는 일은 없다
        return scanProgram(reqDto.expoId, programId, traineeId, participantId = reqDto.participantId, traineeId = traineeId)
    }

    override fun findProgram(
        expoId: String,
        programId: Long,
    ) {
        expoClient.getTrainingPrograms(expoId, TrainingProgramBatchReqDto(listOf(programId)))
    }

    override fun isApplied(
        programId: Long,
        personId: Long,
    ) = applicationClient.checkTrainingApplication(programId, personId).applied

    override fun resolveName(
        expoId: String,
        personId: Long,
    ) = programScanNameResolver.trainee(expoId, personId)

    /**
     * `participantId`와 `code`가 오면 유저 서비스에서 코드를 확인하고 연결된 연수자를 찾는다(틀린 code, 다른 박람회, 연결 없음은 같은
     * 404). 아니면 이전 방식으로 `traineeId`를 받아 연수자가 이 박람회에 있는지만 확인한다.
     */
    private fun resolveTraineeId(reqDto: ScanTrainingProgramReqDto): Long {
        val notFound = ExpectedException(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.")
        val hasParticipant = reqDto.participantId != null
        val hasCode = !reqDto.code.isNullOrBlank()
        if (hasParticipant != hasCode) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "참가자 ID와 코드를 함께 보내야 합니다.")
        }
        if (hasParticipant) {
            return userCircuitBreaker
                .callService("유저", notFound) {
                    userClient.resolveTraineeByParticipant(
                        ResolveTraineeByParticipantReqDto(reqDto.expoId, requireNotNull(reqDto.participantId), requireNotNull(reqDto.code)),
                    )
                }.traineeId
        }
        val traineeId = reqDto.traineeId ?: throw ExpectedException(HttpStatus.BAD_REQUEST, "연수자 ID 또는 참가자 ID와 코드가 필요합니다.")
        userCircuitBreaker.callService("유저", notFound) {
            userClient.getTraineeNames(TraineeNamesReqDto(reqDto.expoId, listOf(traineeId)))
        }
        return traineeId
    }
}
