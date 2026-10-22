package team.startup.expo.domain.attendance.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException

/** 프로그램 스캔 응답에 담을 이름을 유저 서비스에서 읽는다. 출석은 이미 기록됐으므로 읽지 못하면 이름만 비운다. */
@Component
class ProgramScanNameResolver(
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
) {
    fun standard(
        expoId: String,
        participantId: Long,
    ): String? =
        lookup {
            userClient
                .getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, listOf(participantId)))
                .firstOrNull { it.participantId == participantId }
                ?.name
        }

    fun trainee(
        expoId: String,
        traineeId: Long,
    ): String? =
        lookup {
            userClient
                .getTraineeNames(TraineeNamesReqDto(expoId, listOf(traineeId)))
                .firstOrNull { it.traineeId == traineeId }
                ?.name
        }

    private fun lookup(block: () -> String?): String? =
        try {
            userCircuitBreaker.callService("유저") { block() }
        } catch (_: ExpectedException) {
            null
        }
}
