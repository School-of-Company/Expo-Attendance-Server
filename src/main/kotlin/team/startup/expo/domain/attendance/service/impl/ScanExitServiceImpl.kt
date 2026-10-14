package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanExitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanExitResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.ScanExitService
import team.startup.expo.domain.attendance.service.ScannerName
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 퇴장 스캔. 입장과 달리 기간이나 회차를 확인하지 않는다(입장 때 이미 확인했고, 퇴장은 박람회 서비스가 응답하지 못해도 입구를 막지 않는다).
 * 누구의 QR인지는 입장과 같은 방식으로 확인한다: 일반 참가자는 유저 서비스의 `verify`, 연수자는 이름 조회로 존재 확인, 종이 QR은 이 박람회의 토큰인지 확인.
 */
@Service
class ScanExitServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val qrTokenRepository: QrTokenRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) : ScanExitService {
    override fun scan(
        expoId: String,
        reqDto: ScanExitReqDto,
        scannerName: String?,
    ): ScanExitResDto {
        val (type, subjectId) = identify(expoId, reqDto)
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()

        if (entryLogRepository.markExited(expoId, type.name, subjectId, today, now, ScannerName.of(scannerName)) == 1) {
            val log = findLog(expoId, type, subjectId, now)
            return toResponse(log, now)
        }
        // 갱신이 0건이면 오늘 입장 기록이 없거나 이미 퇴장한 것이다
        val existing = findLog(expoId, type, subjectId, now) ?: throw ExpectedException(HttpStatus.BAD_REQUEST, "오늘 입장 기록이 없습니다.")
        if (existing.exitedAt != null) throw ExpectedException(HttpStatus.BAD_REQUEST, "오늘 이미 퇴장 처리된 QR입니다.")
        throw ExpectedException(HttpStatus.BAD_REQUEST, "오늘 입장 기록이 없습니다.")
    }

    private fun findLog(
        expoId: String,
        type: EntrySubjectType,
        subjectId: String,
        now: LocalDateTime,
    ): EntryLog? = entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(expoId, type, subjectId, now.toLocalDate())

    /** 셋 중 하나만 있어야 하고, 각각 입장 때와 같은 방식으로 실제로 있는 QR인지 확인한다. */
    private fun identify(
        expoId: String,
        reqDto: ScanExitReqDto,
    ): Pair<EntrySubjectType, String> {
        val hasParticipant = reqDto.participantId != null || !reqDto.code.isNullOrBlank()
        val modes = listOf(hasParticipant, reqDto.traineeId != null, !reqDto.token.isNullOrBlank()).count { it }
        if (modes != 1) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "참가자 ID와 코드, 연수자 ID, 종이 QR 토큰 중 하나만 보내야 합니다.")
        }
        if (hasParticipant) {
            val participantId = reqDto.participantId
            val code = reqDto.code
            if (participantId == null || code.isNullOrBlank()) {
                throw ExpectedException(HttpStatus.BAD_REQUEST, "참가자 ID와 코드를 함께 보내야 합니다.")
            }
            userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")) {
                userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, participantId, code))
            }
            return EntrySubjectType.STANDARD to participantId.toString()
        }
        val traineeId = reqDto.traineeId
        if (traineeId != null) {
            userCircuitBreaker.callService("유저", ExpectedException(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.")) {
                userClient.getTraineeNames(TraineeNamesReqDto(expoId, listOf(traineeId)))
            }
            return EntrySubjectType.TRAINEE to traineeId.toString()
        }
        val token = requireNotNull(reqDto.token)
        if (!qrTokenRepository.existsByTokenAndExpoId(token, expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "QR 토큰을 찾을 수 없습니다.")
        }
        return EntrySubjectType.PAPER_QR to token
    }

    private fun toResponse(
        log: EntryLog?,
        now: LocalDateTime,
    ): ScanExitResDto {
        requireNotNull(log) { "퇴장을 기록한 입장 로그를 찾을 수 없습니다." }
        val exited = log.exitedAt ?: now
        return ScanExitResDto(
            subjectType = log.subjectType,
            enteredTime = log.enteredAt.format(TIME),
            exitedTime = exited.format(TIME),
            stayedMinutes = Duration.between(log.enteredAt, exited).toMinutes(),
        )
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
