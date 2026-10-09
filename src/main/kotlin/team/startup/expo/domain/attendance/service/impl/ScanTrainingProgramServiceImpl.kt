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
        userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.")) {
            userClient.getTraineeNames(TraineeNamesReqDto(reqDto.expoId, listOf(reqDto.traineeId)))
        }
        expoCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.")) {
            expoClient.getTrainingPrograms(reqDto.expoId, TrainingProgramBatchReqDto(listOf(programId)))
        }
        expoPeriodValidator.checkInProgress(reqDto.expoId)

        val applied =
            applicationCircuitBreaker.callService(
                "신청",
            ) { applicationClient.checkTrainingApplication(programId, reqDto.traineeId) }
        if (!applied.applied) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 참가 중인 유저를 찾지 못 했습니다.")
        }

        // 분 단위로 기록한다(v1과 같음)
        val now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        when (recordProgramAttendanceService.record(ProgramType.TRAINING, programId, reqDto.traineeId, LocalDate.now(clock), now)) {
            ProgramAttendanceResult.ENTERED, ProgramAttendanceResult.ALREADY_ENTERED -> Unit
            ProgramAttendanceResult.PROGRAM_DELETED -> throw ExpectedException(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.")
        }
    }
}
