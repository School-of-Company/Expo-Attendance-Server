package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntryStatus
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryLogService
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.EntryLogWriter
import team.startup.expo.domain.attendance.support.EntryReplayPolicy
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.attendance.support.PreregisterSessionValidator
import team.startup.expo.domain.attendance.support.ScannerName
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.ParticipantEntryStatusReqDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    private val cancellationGuardGate: CancellationGuardGate,
    private val entryLogService: EntryLogService,
    private val entryLogRepository: EntryLogRepository,
    private val entryLogWriter: EntryLogWriter,
    private val entryReplayPolicy: EntryReplayPolicy,
    private val clock: Clock,
    private val fieldEntryFence: FieldEntryFence,
) : ScanEntryService {
    override fun scan(
        expoId: String,
        reqDto: ScanEntryReqDto,
        scannerName: String?,
    ): ScanEntryResDto {
        checkIdentifier(reqDto)
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        expoPeriodValidator.checkInProgress(expoId, today)
        // 사전등록 QR(참가자 ID와 코드)은 신청한 회차 중 하나의 입장 시간이어야 한다. 입장을 기록하기 전에 지금 회차를 고른다.
        val checkedSessionId =
            if (reqDto.authority == EntryAuthority.ROLE_STANDARD && reqDto.participantId != null && !reqDto.code.isNullOrBlank()) {
                val code = reqDto.code
                preregisterSessionValidator.check(expoId, reqDto.participantId) {
                    // 코드가 틀리면 입장 기록과 같은 404여야 해서, 신청 상태를 알리기 전에 먼저 확인한다
                    userCircuitBreaker.callService("유저", participantNotFound(reqDto)) {
                        userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, reqDto.participantId, code))
                    }
                }
            } else {
                null
            }

        // 전화번호도 기록 전에 ID를 조회해 QR과 같은 참가자 키로 취소 보호를 잡는다.
        val participantId =
            if (reqDto.authority == EntryAuthority.ROLE_STANDARD) {
                reqDto.participantId ?: userCircuitBreaker.callService("유저", participantNotFound(reqDto)) {
                    userClient
                        .resolveStandardParticipant(
                            ResolveStandardParticipantReqDto(expoId, requireNotNull(reqDto.phoneNumber)),
                        ).participantId
                }
            } else {
                null
            }
        val scanner = ScannerName.of(scannerName)
        val scanned =
            if (participantId != null) {
                cancellationGuardGate.scanning(expoId, participantId = participantId, attendanceDate = today) {
                    val sessionId = preregisterSessionValidator.checkNotCancelled(expoId, participantId, checkedSessionId, today)
                    recordOrReenter(expoId, reqDto, sessionId, participantId, now, scanner)
                }
            } else {
                recordOrReenter(expoId, reqDto, null, null, now, scanner)
            }

        // 재입장은 입장 로그가 이미 있다. 처음 입장만, 퇴장 기록과 집계를 위한 입장 로그를 남긴다. 입장은 이미 기록되었으므로 로그 실패가 입장을 막지 않는다(#120)
        scanned.entry?.let {
            val subjectType = if (it.participationType == TRAINEE) EntrySubjectType.TRAINEE else EntrySubjectType.STANDARD
            entryLogService.recordEntry(
                expoId,
                subjectType,
                it.id.toString(),
                scannerName = scannerName,
                enteredAt = now.takeIf { subjectType == EntrySubjectType.STANDARD },
            )
        }
        return scanned.response ?: toResponse(requireNotNull(scanned.entry), now.format(TIME))
    }

    /**
     * 유저 서비스에 입장을 기록한다. 오늘 이미 입장했다고(409) 답하면, 오늘 입장 로그에 퇴장 기록이 있을 때만 재입장으로 처리하고
     * 퇴장 없이 다시 찍은 것은 400이다(#148). 유저 서비스의 409는 참가자 정보를 주지 않으므로 재입장 응답은 [reenterResponse]가 만든다.
     */
    private fun recordOrReenter(
        expoId: String,
        reqDto: ScanEntryReqDto,
        preregisterSessionId: Long?,
        participantId: Long?,
        now: LocalDateTime,
        scanner: String?,
    ): Scanned {
        val attempt = participantId?.let { fieldEntryFence.begin(expoId, it, now.toLocalDate()) }
        return try {
            val entry = recordEntry(expoId, reqDto, preregisterSessionId, participantId, now.toLocalDate())
            attempt?.let { fieldEntryFence.finish(it, true) }
            Scanned(entry = entry, response = toResponse(entry, now.format(TIME)))
        } catch (_: AlreadyEnteredToday) {
            if (participantId != null) {
                val status =
                    userCircuitBreaker.callService("유저") {
                        userClient.getEntryStatus(ParticipantEntryStatusReqDto(expoId, participantId, now.toLocalDate()))
                    }
                if (status.participantId != participantId || status.attendanceDate != now.toLocalDate() || !status.entered) {
                    throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "입장 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.")
                }
            }
            attempt?.let { fieldEntryFence.finish(it, true) }
            Scanned(response = reenterResponse(expoId, reqDto, participantId, now, scanner))
        } catch (e: ExpectedException) {
            if (e.status == HttpStatus.NOT_FOUND || e.status == HttpStatus.BAD_REQUEST) {
                attempt?.let { fieldEntryFence.finish(it, false) }
            }
            throw e
        }
    }

    private fun reenterResponse(
        expoId: String,
        reqDto: ScanEntryReqDto,
        knownParticipantId: Long?,
        now: LocalDateTime,
        scanner: String?,
    ): ScanEntryResDto {
        val isTrainee = reqDto.authority == EntryAuthority.ROLE_TRAINEE
        val subjectType = if (isTrainee) EntrySubjectType.TRAINEE else EntrySubjectType.STANDARD
        val subjectId =
            knownParticipantId ?: userCircuitBreaker.callService("유저", alreadyEntered()) {
                userClient
                    .resolveParticipant(
                        ResolveParticipantReqDto(expoId, requireNotNull(reqDto.phoneNumber), reqDto.authority.participationType),
                    ).participantId
            }
        val today = now.toLocalDate()
        val log =
            entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(expoId, subjectType, subjectId.toString(), today)
        // 입장 로그가 없거나(로그 도입 전 입장, 로그 유실) 퇴장하지 않았으면 재입장이 아니다
        if (log?.exitedAt == null) {
            // 방금 같은 스캐너가 찍은 입장이면 타임아웃 뒤 재시도로 보고 직전 결과를 다시 돌려준다(#176). 아무것도 기록하지 않는다
            if (entryReplayPolicy.allows(log, now, scanner)) return replayResponse(expoId, reqDto, subjectId, isTrainee, log!!)
            throw alreadyEntered()
        }

        val name = lookupName(expoId, reqDto, subjectId, isTrainee)
        // 동시에 같은 재입장 스캔이 오면 한쪽만 퇴장 시각을 비운다
        if (entryLogWriter.reenter(expoId, subjectType.name, subjectId.toString(), today, now, scanner) != 1) throw alreadyEntered()

        return ScanEntryResDto(
            id = subjectId,
            name = name,
            phoneNumber = reqDto.phoneNumber?.takeIf { reqDto.participantId == null },
            personalInformationStatus = null,
            participationType = reqDto.authority.participationType,
            badge = null,
            entryStatus = EntryStatus.RE_ENTRY,
            firstEnteredTime = log.enteredAt.format(TIME),
        )
    }

    /**
     * 재생 응답. 입장 응답처럼 `FIRST`와 최초 입장 시각을 돌려주지만, 유저 서비스의 409는 참가자 정보를 주지 않으므로 재입장 응답처럼
     * 이름 외 정보(개인정보 동의 상태, 명찰)는 비어 있다.
     */
    private fun replayResponse(
        expoId: String,
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        isTrainee: Boolean,
        log: EntryLog,
    ) = ScanEntryResDto(
        id = subjectId,
        name = lookupName(expoId, reqDto, subjectId, isTrainee),
        phoneNumber = reqDto.phoneNumber?.takeIf { reqDto.participantId == null },
        personalInformationStatus = null,
        participationType = reqDto.authority.participationType,
        badge = null,
        entryStatus = EntryStatus.FIRST,
        firstEnteredTime = log.enteredAt.format(TIME),
    )

    private fun lookupName(
        expoId: String,
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        isTrainee: Boolean,
    ): String =
        userCircuitBreaker.callService("유저", participantNotFound(reqDto)) {
            if (isTrainee) {
                userClient.getTraineeNames(TraineeNamesReqDto(expoId, listOf(subjectId))).first { it.traineeId == subjectId }.name
            } else {
                userClient
                    .getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, listOf(subjectId)))
                    .first { it.participantId == subjectId }
                    .name
            }
        }

    private fun alreadyEntered() = ExpectedException(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.")

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
        preregisterSessionId: Long?,
        participantId: Long?,
        attendanceDate: LocalDate,
    ): RecordEntryResDto {
        val request = toEntryRequest(expoId, reqDto, preregisterSessionId, participantId, attendanceDate)
        return userCircuitBreaker.callService(
            "유저",
            participantNotFound(reqDto),
            onConflict = { throw AlreadyEnteredToday() },
        ) { userClient.recordEntry(request) }
    }

    /**
     * 일반 참가자가 참가자 ID와 코드로 왔으면 그 값을, 아니면 전화번호를 유저 서비스에 보낸다(ID와 코드가 우선).
     * PRE는 QR의 확인한 회차 또는 전화번호의 오늘 회차를 붙인다. 연수자는 회차와 FIELD 권위를 보내지 않는다.
     */
    private fun toEntryRequest(
        expoId: String,
        reqDto: ScanEntryReqDto,
        preregisterSessionId: Long?,
        participantId: Long?,
        attendanceDate: LocalDate,
    ): RecordEntryReqDto {
        val type = reqDto.authority.participationType
        val field =
            if (participantId != null &&
                preregisterSessionId == null
            ) {
                preregisterSessionValidator.activeField(expoId, participantId, attendanceDate)
            } else {
                null
            }
        val usesParticipantCode =
            reqDto.authority == EntryAuthority.ROLE_STANDARD && reqDto.participantId != null && !reqDto.code.isNullOrBlank()
        return if (usesParticipantCode) {
            RecordEntryReqDto(
                expoId,
                type,
                participantId = reqDto.participantId,
                code = reqDto.code,
                preregisterSessionId = preregisterSessionId,
                attendanceDate = attendanceDate,
                fieldVisitRegistrationId = field?.registrationId,
                fieldVisitGeneration = field?.generation,
            )
        } else {
            RecordEntryReqDto(
                expoId,
                type,
                phoneNumber = reqDto.phoneNumber,
                preregisterSessionId = preregisterSessionId,
                attendanceDate = attendanceDate.takeIf { participantId != null },
                fieldVisitRegistrationId = field?.registrationId,
                fieldVisitGeneration = field?.generation,
            )
        }
    }

    private fun toResponse(
        entry: RecordEntryResDto,
        firstEnteredTime: String,
    ): ScanEntryResDto {
        val isTrainee = entry.participationType == TRAINEE
        // 명찰 대상은 연수자 전원과 교사·예비교사인 일반 참가자다
        val badge =
            if (isTrainee || entry.occupation in BADGE_OCCUPATIONS) {
                BadgeResDto(
                    name = entry.name,
                    school = entry.school,
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
            entryStatus = EntryStatus.FIRST,
            firstEnteredTime = firstEnteredTime,
        )
    }

    /** 스캔 한 번의 결과. 처음 입장은 유저 서비스의 [entry]가, 재입장은 이 서비스가 만든 [response]가 있다. */
    private class Scanned(
        val entry: RecordEntryResDto? = null,
        val response: ScanEntryResDto? = null,
    )

    /** 유저 서비스가 409로 답한 것을 재입장 판정으로 넘기는 내부 신호다. 밖으로 나가지 않는다. */
    private class AlreadyEnteredToday : RuntimeException(null, null, false, false)

    private companion object {
        const val TRAINEE = "TRAINEE"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        // 소속을 받아 명찰에 "소속 이름"으로 찍는 구분: 교사와 예비교사
        val BADGE_OCCUPATIONS = setOf("TEACHER", "PRE_SERVICE_TEACHER")
    }
}
