package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.PreregisterSessionValidator
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate

/**
 * v1 `PreEnterScanQrCodeServiceImpl`의 입장 스캔. 입장 기록은 유저 서비스가 소유하고, 이 서비스는 박람회 기간과
 * 사전등록 회차 시간을 확인한 뒤 유저 서비스에 입장 기록을 요청한다.
 *
 * 다른 서비스를 부르는 동안 DB 트랜잭션을 잡고 있지 않도록 이 서비스에는 `@Transactional`을 두지 않는다.
 */
@Service
class ScanEntryServiceImpl(
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val preregisterSessionValidator: PreregisterSessionValidator,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : ScanEntryService {
    override fun scan(
        expoId: String,
        reqDto: ScanEntryReqDto,
    ): ScanEntryResDto {
        checkIdentifier(reqDto)
        val today = LocalDate.now(clock)
        expoPeriodValidator.checkInProgress(expoId, today)
        // 사전등록 QR(참가자 ID와 코드)은 신청한 회차의 입장 시간이어야 한다. 입장을 기록하기 전에 확인한다.
        if (reqDto.authority == EntryAuthority.ROLE_STANDARD && reqDto.participantId != null && !reqDto.code.isNullOrBlank()) {
            val code = reqDto.code
            preregisterSessionValidator.check(expoId, reqDto.participantId) {
                // 코드가 틀리면 입장 기록과 같은 404여야 해서, 신청 상태를 알리기 전에 먼저 확인한다
                userCircuitBreaker.callService("유저", participantNotFound(reqDto)) {
                    userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, reqDto.participantId, code))
                }
            }
        }

        val entry = recordEntry(expoId, reqDto)

        return toResponse(entry, reqDto)
    }

    private fun participantNotFound(reqDto: ScanEntryReqDto) =
        ExpectedException(
            HttpStatus.NOT_FOUND,
            if (reqDto.authority == EntryAuthority.ROLE_TRAINEE) "연수자를 찾지 못 했습니다." else "행사 참가자를 찾지 못 했습니다.",
        )

    /** 일반 참가자는 전화번호 또는 참가자 ID와 코드가 모두 필요하고, 연수자는 전화번호가 필요하다. */
    private fun checkIdentifier(reqDto: ScanEntryReqDto) {
        val hasPhone = !reqDto.phoneNumber.isNullOrBlank()
        val hasParticipantCode = reqDto.participantId != null && !reqDto.code.isNullOrBlank()
        val partial = (reqDto.participantId != null) != !reqDto.code.isNullOrBlank()

        if (reqDto.authority == EntryAuthority.ROLE_TRAINEE) {
            if (!hasPhone) throw ExpectedException(HttpStatus.BAD_REQUEST, "연수자는 전화번호가 필요합니다.")
            return
        }
        if (partial || (!hasPhone && !hasParticipantCode)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "전화번호 또는 참가자 ID와 코드가 필요합니다.")
        }
    }

    private fun recordEntry(
        expoId: String,
        reqDto: ScanEntryReqDto,
    ): RecordEntryResDto {
        val request = toEntryRequest(expoId, reqDto)
        return userCircuitBreaker.callService(
            "유저",
            participantNotFound(reqDto),
            onConflict = { throw ExpectedException(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") },
        ) { userClient.recordEntry(request) }
    }

    /** 일반 참가자가 참가자 ID와 코드로 왔으면 그 값을, 아니면 전화번호를 유저 서비스에 보낸다(ID와 코드가 우선). */
    private fun toEntryRequest(
        expoId: String,
        reqDto: ScanEntryReqDto,
    ): RecordEntryReqDto {
        val type = reqDto.authority.participationType
        val usesParticipantCode =
            reqDto.authority == EntryAuthority.ROLE_STANDARD && reqDto.participantId != null && !reqDto.code.isNullOrBlank()
        return if (usesParticipantCode) {
            RecordEntryReqDto(expoId, type, participantId = reqDto.participantId, code = reqDto.code)
        } else {
            RecordEntryReqDto(expoId, type, phoneNumber = reqDto.phoneNumber)
        }
    }

    private fun toResponse(
        entry: RecordEntryResDto,
        reqDto: ScanEntryReqDto,
    ): ScanEntryResDto {
        val isTrainee = entry.participationType == TRAINEE
        // 명찰 대상은 연수자 전원과 교사·예비교사인 일반 참가자다
        val badge =
            if (isTrainee || entry.occupation in BADGE_OCCUPATIONS) {
                BadgeResDto(
                    name = entry.name,
                    school = entry.school,
                    qrCode = badgeQrCode(isTrainee, entry, reqDto),
                )
            } else {
                null
            }

        return ScanEntryResDto(
            id = entry.id,
            name = entry.name,
            phoneNumber = entry.phoneNumber,
            personalInformationStatus = entry.personalInformationStatus,
            participationType = entry.participationType,
            badge = badge,
        )
    }

    /**
     * 명찰 QR은 입구 스캔이 읽는 값이다. 일반 참가자가 ID와 코드로 입장했으면 `{"participantId": 42, "code": "…"}`,
     * 그 외는 이전 형식 `{"participantId": 42, "phoneNumber": "010…"}`(연수자는 `traineeId`)이다.
     */
    private fun badgeQrCode(
        isTrainee: Boolean,
        entry: RecordEntryResDto,
        reqDto: ScanEntryReqDto,
    ): String {
        if (!isTrainee && reqDto.participantId != null && !reqDto.code.isNullOrBlank()) {
            return objectMapper.writeValueAsString(linkedMapOf("participantId" to entry.id, "code" to reqDto.code))
        }
        return objectMapper.writeValueAsString(
            linkedMapOf(
                (if (isTrainee) "traineeId" else "participantId") to entry.id,
                "phoneNumber" to entry.phoneNumber,
            ),
        )
    }

    private companion object {
        const val TRAINEE = "TRAINEE"

        // 소속을 받아 명찰에 "소속 이름"으로 찍는 구분: 교사와 예비교사
        val BADGE_OCCUPATIONS = setOf("TEACHER", "PRE_SERVICE_TEACHER")
    }
}
