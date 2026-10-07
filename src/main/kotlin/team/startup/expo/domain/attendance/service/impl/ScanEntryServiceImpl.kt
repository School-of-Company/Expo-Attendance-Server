package team.startup.expo.domain.attendance.service.impl

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.RecordEntryEventService
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
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
    private val expoClient: ExpoClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    @Qualifier("expoCircuitBreaker") private val expoCircuitBreaker: CircuitBreaker,
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
        checkInProgress(expoId, today)

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

    private fun checkInProgress(
        expoId: String,
        today: LocalDate,
    ) {
        val period =
            try {
                expoCircuitBreaker.executeSupplier { expoClient.getPeriod(expoId) }
            } catch (_: FeignException.NotFound) {
                throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
            } catch (e: Exception) {
                throw unavailable("박람회", e)
            }

        // v1과 같이 시작일과 종료일을 모두 포함한다
        val inProgress = today >= LocalDate.parse(period.startedDay) && today <= LocalDate.parse(period.finishedDay)
        if (!inProgress) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.")
        }
    }

    private fun recordEntry(
        expoId: String,
        reqDto: ScanEntryReqDto,
        today: LocalDate,
    ): RecordEntryResDto {
        val request = toEntryRequest(expoId, reqDto)
        return try {
            userCircuitBreaker.executeSupplier { userClient.recordEntry(request) }
        } catch (_: FeignException.NotFound) {
            throw ExpectedException(
                HttpStatus.NOT_FOUND,
                if (reqDto.authority == EntryAuthority.ROLE_TRAINEE) "연수자를 찾지 못 했습니다." else "행사 참가자를 찾지 못 했습니다.",
            )
        } catch (_: FeignException.Conflict) {
            // 입장은 기록됐는데 이벤트 기록이 빠졌을 수 있다. 같은 QR을 다시 찍으면 이벤트가 만들어지게 한다.
            // 참가자 ID와 코드로 온 요청은 문자를 받을 번호를 알 수 없어서(409에 정보가 없음) 복구하지 않는다.
            if (reqDto.authority == EntryAuthority.ROLE_STANDARD && request.phoneNumber != null) {
                recoverEntryEvent(request, request.phoneNumber, today)
            }
            throw ExpectedException(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.")
        } catch (e: Exception) {
            throw unavailable("유저", e)
        }
    }

    /**
     * 입장은 기록됐는데 이벤트가 빠진 상태에서 다시 스캔한 경우다. 복구가 끝났거나 이벤트가 이미 있을 때만 호출자가
     * "이미 입장"(400)을 받는다. 조회나 저장이 실패하면 복구가 끝나지 않았으므로 재시도할 수 있게 503으로 알린다.
     */
    private fun recoverEntryEvent(
        request: RecordEntryReqDto,
        phoneNumber: String,
        today: LocalDate,
    ) {
        val participant =
            try {
                userCircuitBreaker.executeSupplier {
                    userClient.resolveParticipant(ResolveParticipantReqDto(request.expoId, phoneNumber, request.participationType))
                }
            } catch (e: Exception) {
                throw unavailable("유저", e)
            }

        try {
            saveEntryEvent(request.expoId, participant.participantId, phoneNumber, today)
        } catch (e: Exception) {
            // 전화번호가 들어 있는 값은 로그에 남기지 않는다
            logger.warn("입장 이벤트 복구 저장 실패: expoId={}, 원인={}", request.expoId, e.javaClass.simpleName)
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "입장 이벤트를 기록하지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }

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

    /** "없음(404)"과 달리 호출 자체가 실패한 경우다. 회로가 열려 있어도 같은 응답이다. */
    private fun unavailable(
        service: String,
        cause: Exception,
    ): ExpectedException {
        if (cause is FeignException || cause is CallNotPermittedException) {
            logger.warn("{} 서비스 호출 실패: {}", service, cause.javaClass.simpleName)
        } else {
            logger.warn("{} 서비스 호출 중 예상하지 못한 오류: {}", service, cause.javaClass.simpleName)
        }
        return ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "$service 서비스를 잠시 사용할 수 없습니다.")
    }

    private companion object {
        const val TRAINEE = "TRAINEE"

        // 소속을 받아 명찰에 "소속 이름"으로 찍는 구분: 교사와 예비교사
        val BADGE_OCCUPATIONS = setOf("TEACHER", "PRE_SERVICE_TEACHER")
    }
}
