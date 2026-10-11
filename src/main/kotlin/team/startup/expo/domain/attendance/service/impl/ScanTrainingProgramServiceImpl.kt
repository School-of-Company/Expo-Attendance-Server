package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import team.startup.expo.domain.attendance.service.ScanTrainingProgramService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.user.ResolveTraineeByParticipantReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** v1 `ScanTrainingProByQrCodeServiceImpl`의 연수 프로그램 출석. 동작은 일반 프로그램과 같다. */
@Service
class ScanTrainingProgramServiceImpl(
    private val userClient: UserClient,
    private val expoClient: ExpoClient,
    private val applicationClient: ApplicationClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
    @Qualifier("applicationCircuitBreaker") private val applicationCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val recordProgramAttendanceService: RecordProgramAttendanceService,
    private val clock: Clock,
) : ScanTrainingProgramService {
    override fun scan(
        programId: Long,
        reqDto: ScanTrainingProgramReqDto,
    ) {
        val traineeId = resolveTraineeId(reqDto)
        expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.")) {
            expoClient.getTrainingPrograms(reqDto.expoId, TrainingProgramBatchReqDto(listOf(programId)))
        }
        expoPeriodValidator.checkInProgress(reqDto.expoId)

        val applied =
            applicationCircuitBreaker.callService(
                "신청",
            ) { applicationClient.checkTrainingApplication(programId, traineeId) }
        if (!applied.applied) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 참가 중인 유저를 찾지 못 했습니다.")
        }

        // 분 단위로 기록한다(v1과 같음)
        val now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        when (recordProgramAttendanceService.record(ProgramType.TRAINING, programId, traineeId, LocalDate.now(clock), now)) {
            ProgramAttendanceResult.ENTERED, ProgramAttendanceResult.ALREADY_ENTERED -> Unit
            ProgramAttendanceResult.PROGRAM_DELETED -> throw ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.")
        }
    }

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
