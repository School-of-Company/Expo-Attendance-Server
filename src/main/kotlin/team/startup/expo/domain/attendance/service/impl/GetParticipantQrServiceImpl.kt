package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ParticipantQrResDto
import team.startup.expo.domain.attendance.service.GetParticipantQrService
import team.startup.expo.domain.attendance.support.PreregisterSessionValidator
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.ParticipantQrReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException

@Service
class GetParticipantQrServiceImpl(
    private val preregisterSessionValidator: PreregisterSessionValidator,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
) : GetParticipantQrService {
    override fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetParticipantQrReqDto,
    ): ParticipantQrResDto {
        val notFound = ExpectedException(HttpStatus.NOT_FOUND, "QR을 찾지 못 했습니다.")
        // 입장 판정과 같은 기준이다: 회차 기록이 없거나 현장 재등록한 참가자는 확인하지 않고(Application #250, #186),
        // 기록이 있으면 확정 상태인 회차가 하나라도 있어야 한다(#160). 대기·미확정 여부는 호출하는 신청 서비스가 먼저 판정한다
        if (preregisterSessionValidator.isCancelled(expoId, participantId)) throw notFound

        val qr =
            userCircuitBreaker.callService("유저", notFound) {
                userClient.getParticipantQr(ParticipantQrReqDto(expoId, reqDto.representativeId, participantId))
            }
        // 유저가 다른 참가자의 값을 돌려주면 내주지 않는다
        if (qr.participantId != participantId) throw notFound
        return ParticipantQrResDto(qr.participantId, qr.code)
    }
}
