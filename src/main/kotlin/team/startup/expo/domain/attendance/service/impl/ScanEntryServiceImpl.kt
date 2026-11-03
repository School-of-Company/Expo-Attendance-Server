package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.service.EntryLogService
import team.startup.expo.domain.attendance.service.ScanEntryService
import team.startup.expo.domain.attendance.support.AlreadyEnteredToday
import team.startup.expo.domain.attendance.support.CancellationGuardGate
import team.startup.expo.domain.attendance.support.EntryOutcomeUnknown
import team.startup.expo.domain.attendance.support.EntryResponses
import team.startup.expo.domain.attendance.support.EntryUserGateway
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.attendance.support.FieldEntryFence
import team.startup.expo.domain.attendance.support.PreregisterSessionValidator
import team.startup.expo.domain.attendance.support.RepeatEntryResolver
import team.startup.expo.domain.attendance.support.ScannerName
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * v1 `PreEnterScanQrCodeServiceImpl`의 입장 스캔. 입장 기록은 유저 서비스가 소유하고, 이 서비스는 박람회 기간과
 * 사전등록 회차 시간을 확인한 뒤 유저 서비스에 입장 기록을 요청한다.
 *
 * 흐름만 여기에 둔다. 유저 서비스 호출은 [EntryUserGateway], "이미 입장"(409) 뒤 판정은 [RepeatEntryResolver],
 * 응답 조립은 [EntryResponses]가 맡는다.
 *
 * 다른 서비스를 부르는 동안 DB 트랜잭션을 잡고 있지 않도록 이 서비스에는 `@Transactional`을 두지 않는다.
 */
@Service
class ScanEntryServiceImpl(
    private val users: EntryUserGateway,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val preregisterSessionValidator: PreregisterSessionValidator,
    private val cancellationGuardGate: CancellationGuardGate,
    private val entryLogService: EntryLogService,
    private val repeatEntryResolver: RepeatEntryResolver,
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
        val checkedSessionId = checkPreregisterSession(expoId, reqDto)

        // 전화번호도 기록 전에 ID를 조회해 QR과 같은 참가자 키로 취소 보호를 잡는다.
        val participantId =
            if (reqDto.authority == EntryAuthority.ROLE_STANDARD) {
                reqDto.participantId ?: users.resolveStandardParticipant(expoId, requireNotNull(reqDto.phoneNumber), reqDto.authority)
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
            recordEntryLog(expoId, subjectType, it.id, scannerName, now)
        }
        // 기록 응답을 잃었지만 유저 서비스에 입장이 확인된 참가자도 정상 첫 입장처럼 로그를 남긴다(#287).
        // 이름 조회는 락 밖에서 한다. User가 느린 바로 그때 락 트랜잭션이 커넥션을 더 오래 쥐지 않게 한다.
        scanned.recoveredParticipantId?.let {
            recordEntryLog(expoId, EntrySubjectType.STANDARD, it, scannerName, now)
            val name = users.name(expoId, it, isTrainee = false, authority = reqDto.authority)
            return EntryResponses.restored(reqDto, it, name, now)
        }
        return scanned.response ?: EntryResponses.first(requireNotNull(scanned.entry), now)
    }

    /**
     * 사전등록 QR(참가자 ID와 코드)은 신청한 회차 중 하나의 입장 시간이어야 한다. 입장을 기록하기 전에 지금 회차를 고른다.
     * 코드가 틀리면 입장 기록과 같은 404여야 해서, 신청 상태를 알리기 전에 먼저 확인한다.
     */
    private fun checkPreregisterSession(
        expoId: String,
        reqDto: ScanEntryReqDto,
    ): Long? {
        val participantId = reqDto.participantId
        val code = reqDto.code
        if (reqDto.authority != EntryAuthority.ROLE_STANDARD || participantId == null || code.isNullOrBlank()) return null
        return preregisterSessionValidator.check(
            expoId,
            participantId,
            occupation = { users.occupation(expoId, participantId) },
        ) { users.verifyCode(expoId, participantId, code, reqDto.authority) }
    }

    private fun recordEntryLog(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: Long,
        scannerName: String?,
        now: LocalDateTime,
    ) {
        entryLogService.recordEntry(
            expoId,
            subjectType,
            subjectId.toString(),
            scannerName = scannerName,
            enteredAt = now.takeIf { subjectType == EntrySubjectType.STANDARD },
        )
    }

    /**
     * 유저 서비스에 입장을 기록한다. 오늘 이미 입장했다고(409) 답하면 [RepeatEntryResolver]가 재입장·재생·로그 채움·400을 정한다.
     *
     * 일반 참가자의 기록 호출이 타임아웃처럼 결과를 모르게 끝나면 유저 서비스에 오늘 입장 여부를 한 번 묻고, 기록돼 있으면 첫 입장으로
     * 마무리한다(#287). 연수자는 입장 여부를 물을 계약이 없어 지금처럼 503이다.
     */
    private fun recordOrReenter(
        expoId: String,
        reqDto: ScanEntryReqDto,
        preregisterSessionId: Long?,
        participantId: Long?,
        now: LocalDateTime,
        scanner: String?,
    ): Scanned {
        val today = now.toLocalDate()
        val attempt = participantId?.let { fieldEntryFence.begin(expoId, it, today) }
        return try {
            val request = toEntryRequest(expoId, reqDto, preregisterSessionId, participantId, today)
            val entry = users.recordEntry(request, reqDto.authority, detectOutcomeUnknown = participantId != null)
            attempt?.let { fieldEntryFence.finish(it, true) }
            Scanned(entry = entry)
        } catch (_: AlreadyEnteredToday) {
            if (participantId != null && !users.enteredToday(expoId, participantId, today)) {
                throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "입장 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.")
            }
            attempt?.let { fieldEntryFence.finish(it, true) }
            Scanned(response = repeatEntryResolver.resolve(expoId, reqDto, participantId, now, scanner))
        } catch (_: EntryOutcomeUnknown) {
            // 유저 서비스가 기록했는지 모른다. 오늘 입장이 기록돼 있지 않거나 확인하지 못하면 지금처럼 503이고 fence는 PENDING으로 남는다.
            // entry-status는 회차를 구분하지 않으므로, 오늘 입장 로그가 이미 있으면(다른 회차로 먼저 입장) 이번 회차가 기록됐는지 알 수 없어
            // 묻지 않고 503이다. 다시 찍으면 기록되었으면 409 경로, 아니면 새로 기록된다.
            val id = requireNotNull(participantId)
            if (repeatEntryResolver.hasTodayLog(expoId, id, today) || !users.enteredToday(expoId, id, today)) {
                throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.")
            }
            attempt?.let { fieldEntryFence.finish(it, true) }
            Scanned(recoveredParticipantId = id)
        } catch (e: ExpectedException) {
            if (e.status == HttpStatus.NOT_FOUND || e.status == HttpStatus.BAD_REQUEST) {
                attempt?.let { fieldEntryFence.finish(it, false) }
            }
            throw e
        }
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
            if (participantId != null && preregisterSessionId == null) {
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

    /**
     * 스캔 한 번의 결과. 처음 입장은 유저 서비스의 [entry]가, 재입장·재생·복구는 이 서비스가 만든 [response]가 있다.
     * [recoveredParticipantId]는 결과를 모르던 기록을 유저 서비스에서 확인한 참가자로, 처음 입장처럼 입장 로그를 남기고
     * 락 밖에서 이름을 읽어 응답을 만든다(#287).
     */
    private class Scanned(
        val entry: RecordEntryResDto? = null,
        val response: ScanEntryResDto? = null,
        val recoveredParticipantId: Long? = null,
    )

    private companion object {
        const val TRAINEE = "TRAINEE"
    }
}
