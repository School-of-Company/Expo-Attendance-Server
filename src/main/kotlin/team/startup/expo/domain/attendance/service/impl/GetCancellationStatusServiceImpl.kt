package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.CancellationStatusResDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.GetCancellationStatusService
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException

@Service
class GetCancellationStatusServiceImpl(
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val standardProgramAttendanceRepository: StandardProgramAttendanceRepository,
    private val trainingProgramAttendanceRepository: TrainingProgramAttendanceRepository,
    private val cancellationGuardRepository: CancellationGuardRepository,
) : GetCancellationStatusService {
    override fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetCancellationStatusReqDto,
    ): CancellationStatusResDto {
        // 전화번호는 유저 서비스에 본문으로만 넘기고 이 서비스는 저장하거나 기록하지 않는다. 모르는 쌍은 구분 없이 404다
        val user =
            userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾지 못 했습니다.")) {
                userClient.getCancellationStatus(CancellationStatusReqDto(expoId, reqDto.phoneNumber, participantId))
            }
        val traineeId = user.linkedTraineeId
        val programAttended =
            standardProgramAttendanceRepository.existsByParticipantId(participantId) ||
                (traineeId != null && trainingProgramAttendanceRepository.existsByTraineeId(traineeId))
        val guard = cancellationGuardRepository.findByExpoIdAndParticipantId(expoId, participantId)
        return CancellationStatusResDto(
            participantId = participantId,
            representative = user.representative,
            entered = user.entered,
            programAttended = programAttended,
            traineeLinked = user.traineeLinked,
            linkedTraineeId = traineeId,
            guardState = guard?.state,
        )
    }
}
