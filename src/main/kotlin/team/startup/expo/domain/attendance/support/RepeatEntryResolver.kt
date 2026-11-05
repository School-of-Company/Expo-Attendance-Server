package team.startup.expo.domain.attendance.support

import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 유저 서비스가 "오늘 이미 입장"(409)이라고 답한 스캔의 판정. 유저 서비스의 409는 참가자 정보를 주지 않으므로 응답은 여기서 만든다.
 *
 * - 오늘 입장 로그가 없으면 빠진 로그를 채우고 첫 입장으로 응답한다(#287)
 * - 퇴장 기록이 있으면 재입장이다(#148)
 * - 퇴장 없이 다시 찍었으면 400이다. 방금 같은 스캐너가 찍은 입장이면 타임아웃 뒤 재시도로 보고 직전 결과를 다시 돌려준다(#176)
 */
@Component
class RepeatEntryResolver(
    private val entryLogRepository: EntryLogRepository,
    private val entryLogWriter: EntryLogWriter,
    private val entryReplayPolicy: EntryReplayPolicy,
    private val users: EntryUserGateway,
) {
    fun resolve(
        expoId: String,
        reqDto: ScanEntryReqDto,
        knownParticipantId: Long?,
        now: LocalDateTime,
        scanner: String?,
    ): ScanEntryResDto {
        val isTrainee = reqDto.authority == EntryAuthority.ROLE_TRAINEE
        val subjectType = if (isTrainee) EntrySubjectType.TRAINEE else EntrySubjectType.STANDARD
        val subjectId =
            knownParticipantId ?: users.resolveParticipant(expoId, requireNotNull(reqDto.phoneNumber), reqDto.authority)
        val today = now.toLocalDate()
        var log = findTodayLog(expoId, subjectType, subjectId, today)
        if (log == null) {
            fillMissingLog(expoId, reqDto, subjectType, subjectId, isTrainee, now, scanner)?.let { return it }
            // 같은 사람을 동시에 찍은 다른 스캔이 먼저 채웠다. 그 로그로 아래 규칙을 따른다
            log = findTodayLog(expoId, subjectType, subjectId, today) ?: throw EntryErrors.alreadyEntered()
        }
        // 퇴장하지 않았으면 재입장이 아니다
        if (log.exitedAt == null) {
            if (entryReplayPolicy.allows(log, now, scanner)) {
                return EntryResponses.restored(reqDto, subjectId, name(expoId, reqDto, subjectId, isTrainee), log.enteredAt)
            }
            throw EntryErrors.alreadyEntered()
        }

        val name = name(expoId, reqDto, subjectId, isTrainee)
        // 동시에 같은 재입장 스캔이 오면 한쪽만 퇴장 시각을 비운다
        if (entryLogWriter.reenter(expoId, subjectType.name, subjectId.toString(), today, now, scanner) != 1) {
            throw EntryErrors.alreadyEntered()
        }
        return EntryResponses.reEntry(reqDto, subjectId, name, log.enteredAt)
    }

    /** 일반 참가자의 오늘 입장 로그가 있는지. 결과를 모르는 기록을 User에 확인해도 되는지 가린다(#287). */
    fun hasTodayLog(
        expoId: String,
        participantId: Long,
        today: LocalDate,
    ): Boolean = findTodayLog(expoId, EntrySubjectType.STANDARD, participantId, today) != null

    private fun findTodayLog(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: Long,
        today: LocalDate,
    ): EntryLog? =
        entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(expoId, subjectType, subjectId.toString(), today)

    /**
     * 유저 서비스는 오늘 입장을 기록했는데(409) 오늘 입장 로그가 없으면(기록 응답을 잃었거나 로그를 남기지 못함, 로그 도입 전 입장)
     * 빠진 로그와 입장 이력(`ENTER`)을 지금 시각으로 채우고 첫 입장 응답을 돌려준다(#287). 유저 서비스는 입장 시각을 주지 않는다.
     * 같은 사람을 동시에 찍어 다른 스캔이 먼저 채웠으면 `null`이고, 호출하는 쪽이 그 로그로 기존 규칙(재생, 400)을 따른다.
     * 이름을 먼저 읽어, 조회가 실패하면 로그를 남기지 않고 실패한다(다시 찍으면 다시 채운다).
     */
    private fun fillMissingLog(
        expoId: String,
        reqDto: ScanEntryReqDto,
        subjectType: EntrySubjectType,
        subjectId: Long,
        isTrainee: Boolean,
        now: LocalDateTime,
        scanner: String?,
    ): ScanEntryResDto? {
        val name = name(expoId, reqDto, subjectId, isTrainee)
        val inserted = entryLogWriter.insertIfAbsent(expoId, subjectType.name, subjectId.toString(), now.toLocalDate(), now, null, scanner)
        return if (inserted == 1) EntryResponses.restored(reqDto, subjectId, name, now) else null
    }

    private fun name(
        expoId: String,
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        isTrainee: Boolean,
    ) = users.name(expoId, subjectId, isTrainee, reqDto.authority)
}
