package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.PreregisterSessionValidator
import team.startup.expo.domain.attendance.service.RecordEntryEventService
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.StandardParticipantBriefsReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * v1 `PreEnterScanQrCodeServiceImpl`의 입장 스캔. 입장 기록은 유저 서비스가 소유하고, 이 서비스는 박람회 기간을
 * 확인한 뒤 유저 서비스에 입장 기록을 요청하고 일반 참가자의 설문 문자용 이벤트를 아웃박스에 남긴다.
 *
 * 다른 서비스를 부르는 동안 DB 트랜잭션을 잡고 있지 않도록 이 서비스에는 `@Transactional`을 두지 않는다.
 * 아웃박스 기록은 리포지토리 호출 하나가 각자 트랜잭션이다.
 */
@Service
class ScanEntryServiceImpl(
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val preregisterSessionValidator: PreregisterSessionValidator,
    private val recordEntryEventService: RecordEntryEventService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : ScanEntryService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun scan(
        expoId: String,
        reqDto: ScanEntryReqDto,
    ): ScanEntryResDto {
        checkIdentifier(reqDto)
        val today = LocalDate.now(clock)
        expoPeriodValidator.checkInProgress(expoId, today)
        // 사전등록 QR(참가자 ID와 코드)은 신청한 회차의 입장 시간이어야 한다. 입장을 기록하기 전에 확인한다.
        if (reqDto.authority == EntryAuthority.ROLE_STANDARD && reqDto.participantId != null && !reqDto.code.isNullOrBlank()) {
            preregisterSessionValidator.check(expoId, reqDto.participantId)
        }

        val entry = recordEntry(expoId, reqDto, today)

        if (reqDto.authority == EntryAuthority.ROLE_STANDARD) {
            // 문자는 본인 번호로, 번호가 없는 동행자는 대표자 번호로 보낸다. 받을 번호가 없으면 이벤트를 남기지 않는다.
            val notificationPhone = entry.notificationPhoneNumber ?: entry.phoneNumber
            if (notificationPhone != null) {
                saveEntryEvent(expoId, entry.id, notificationPhone, today)
            } else {
                logger.info("입장 이벤트를 남기지 않습니다(문자를 받을 번호 없음): participantId={}", entry.id)
            }
        }
        return toResponse(entry, reqDto)
    }

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
        today: LocalDate,
    ): RecordEntryResDto {
        val request = toEntryRequest(expoId, reqDto)
        val notFound =
            ExpectedException(
                HttpStatus.NOT_FOUND,
                if (reqDto.authority == EntryAuthority.ROLE_TRAINEE) "연수자를 찾지 못 했습니다." else "행사 참가자를 찾지 못 했습니다.",
            )
        return userCircuitBreaker.callService(
            "유저",
            notFound,
            onConflict = {
                // 입장은 기록됐는데 이벤트 기록이 빠졌을 수 있다. 같은 QR을 다시 찍으면 이벤트가 만들어지게 한다.
                if (reqDto.authority == EntryAuthority.ROLE_STANDARD) recoverEntryEvent(expoId, request, today)
                throw ExpectedException(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.")
            },
        ) { userClient.recordEntry(request) }
    }

    /**
     * 입장은 기록됐는데 이벤트가 빠진 상태에서 다시 스캔한 경우다. 참가자 ID(요청에 없으면 전화번호로 조회)로 유저 서비스에서
     * 문자를 받을 번호를 다시 읽어 이벤트를 만든다. 요청에 들어온 원문 번호가 아니라 유저 서비스 기준 값을 써서, 정상 경로가 남긴
     * 이벤트와 같은 번호 문자열로 하루 한 통 규칙이 지켜진다.
     *
     * 복구가 끝났거나 이벤트가 이미 있을 때만 호출자가 "이미 입장"(400)을 받는다. 조회나 저장이 실패하면 복구가 끝나지 않았으므로
     * 재시도할 수 있게 503으로 알린다. 문자를 받을 번호가 없으면(번호 없는 동행자, 유저 서비스가 대표자 번호를 내려주기 전) 복구할
     * 수 없어 이벤트 없이 400으로 끝난다.
     */
    private fun recoverEntryEvent(
        expoId: String,
        request: RecordEntryReqDto,
        today: LocalDate,
    ) {
        val participantId = request.participantId ?: resolveParticipantId(expoId, request)
        val brief =
            userCircuitBreaker
                .callService("유저") {
                    userClient.getStandardParticipantBriefs(StandardParticipantBriefsReqDto(expoId, listOf(participantId))).first()
                }

        val notificationPhone = brief.notificationPhoneNumber ?: brief.phoneNumber
        if (notificationPhone == null) {
            logger.info("입장 이벤트를 복구하지 못합니다(문자를 받을 번호 없음): participantId={}", participantId)
            return
        }

        try {
            saveEntryEvent(expoId, participantId, notificationPhone, today)
        } catch (e: Exception) {
            // 전화번호가 들어 있는 값은 로그에 남기지 않는다
            logger.warn("입장 이벤트 복구 저장 실패: expoId={}, 원인={}", expoId, e.javaClass.simpleName)
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "입장 이벤트를 기록하지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }

    /** 전화번호로 온 요청의 참가자 ID를 찾는다. 번호는 요청에 있어야 한다(`checkIdentifier`가 보장). */
    private fun resolveParticipantId(
        expoId: String,
        request: RecordEntryReqDto,
    ): Long =
        userCircuitBreaker
            .callService("유저") {
                userClient.resolveParticipant(
                    ResolveParticipantReqDto(expoId, requireNotNull(request.phoneNumber), request.participationType),
                )
            }.participantId

    /** 같은 날 같은 번호의 이벤트는 한 번만 남는다(번호 하나로 설문 문자는 하루 한 통). */
    private fun saveEntryEvent(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
        today: LocalDate,
    ) {
        recordEntryEventService.record(expoId, participantId, phoneNumber, today)
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
