package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.PreregisterSessionStatus
import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto
import team.startup.expo.domain.attendance.repository.PreregisterSessionRepository
import team.startup.expo.domain.attendance.service.GetParticipantQrService
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.ParticipantQrReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException

@Service
class GetParticipantQrServiceImpl(
    private val preregisterSessionRepository: PreregisterSessionRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
) : GetParticipantQrService {
    override fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto {
        val notFound = ExpectedException(HttpStatus.NOT_FOUND, "QR을 찾지 못 했습니다.")
        // 회차 기록은 확정·승급 때만 생기므로, 기록이 없거나 취소된 참가자(대기 포함)에게는 QR을 내주지 않는다
        val assignment = preregisterSessionRepository.findByExpoIdAndParticipantId(expoId, participantId)
        if (assignment == null || assignment.status != PreregisterSessionStatus.ACTIVE) throw notFound

        val qr =
            userCircuitBreaker.callService("유저", notFound) {
                userClient.getParticipantQr(ParticipantQrReqDto(expoId, reqDto.representativeId, participantId))
            }
        // 유저가 다른 참가자의 값을 돌려주면 내주지 않는다
        if (qr.participantId != participantId) throw notFound
        return ParticipantQrResDto(qr.participantId, qr.code)
    }
}
