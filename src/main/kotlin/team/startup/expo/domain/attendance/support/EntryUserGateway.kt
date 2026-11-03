package team.startup.expo.domain.attendance.support

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.EntryCardsReqDto
import team.startup.expo.global.client.user.ParticipantEntryStatusReqDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import java.time.LocalDate

/**
 * 입장 스캔이 유저 서비스에 묻는 것들. 모든 호출은 유저 회로 차단기를 거치고(404·409는 결과, 그 밖은 503),
 * 입장 기록의 409와 결과 모름은 호출하는 쪽이 판정할 수 있게 [AlreadyEnteredToday]·[EntryOutcomeUnknown]으로 알린다.
 */
@Component
class EntryUserGateway(
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
) {
    /** 참가자 ID와 코드가 맞는지 확인한다. 틀리면 입장 기록과 같은 404다. */
    fun verifyCode(
        expoId: String,
        participantId: Long,
        code: String,
        authority: EntryAuthority,
    ) {
        userCircuitBreaker.callService("유저", EntryErrors.participantNotFound(authority)) {
            userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, participantId, code))
        }
    }

    /** 일반 참가자 전화번호 입장을 기록 전에 참가자 ID로 바꾼다. */
    fun resolveStandardParticipant(
        expoId: String,
        phoneNumber: String,
        authority: EntryAuthority,
    ): Long =
        userCircuitBreaker.callService("유저", EntryErrors.participantNotFound(authority)) {
            userClient.resolveStandardParticipant(ResolveStandardParticipantReqDto(expoId, phoneNumber)).participantId
        }

    /** 409 뒤 재입장 판정에 쓸 ID. 이미 입장한 사람이라 찾지 못하면 "이미 입장"으로 답한다. */
    fun resolveParticipant(
        expoId: String,
        phoneNumber: String,
        authority: EntryAuthority,
    ): Long =
        userCircuitBreaker.callService("유저", EntryErrors.alreadyEntered()) {
            userClient.resolveParticipant(ResolveParticipantReqDto(expoId, phoneNumber, authority.participationType)).participantId
        }

    /**
     * 입장을 기록한다. 오늘 이미 입장했으면 [AlreadyEnteredToday], [detectOutcomeUnknown]이고 타임아웃처럼 결과를 모르게 끝나면
     * [EntryOutcomeUnknown]을 던진다(#287).
     */
    fun recordEntry(
        request: RecordEntryReqDto,
        authority: EntryAuthority,
        detectOutcomeUnknown: Boolean,
    ): RecordEntryResDto =
        userCircuitBreaker.callService(
            "유저",
            EntryErrors.participantNotFound(authority),
            onConflict = { throw AlreadyEnteredToday() },
            onOutcomeUnknown = OUTCOME_UNKNOWN.takeIf { detectOutcomeUnknown },
        ) { userClient.recordEntry(request) }

    /** 이 참가자의 그날 입장이 기록돼 있는지 묻는다. 응답이 다른 참가자·날짜를 가리키면 기록되지 않은 것으로 본다. */
    fun enteredToday(
        expoId: String,
        participantId: Long,
        date: LocalDate,
    ): Boolean {
        val status =
            userCircuitBreaker.callService("유저") {
                userClient.getEntryStatus(ParticipantEntryStatusReqDto(expoId, participantId, date))
            }
        return status.participantId == participantId && status.attendanceDate == date && status.entered
    }

    fun name(
        expoId: String,
        subjectId: Long,
        isTrainee: Boolean,
        authority: EntryAuthority,
    ): String =
        userCircuitBreaker.callService("유저", EntryErrors.participantNotFound(authority)) {
            if (isTrainee) {
                userClient.getTraineeNames(TraineeNamesReqDto(expoId, listOf(subjectId))).first { it.traineeId == subjectId }.name
            } else {
                userClient
                    .getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, listOf(subjectId)))
                    .first { it.participantId == subjectId }
                    .name
            }
        }

    /** 교원·예비교사 당일 입장(#283)을 위해 클라이언트가 아니라 유저 서비스가 가진 직업을 읽는다. */
    fun occupation(
        expoId: String,
        participantId: Long,
    ): String? =
        userCircuitBreaker
            .callService("유저") { userClient.getEntryCards(EntryCardsReqDto(expoId, listOf(participantId), emptyList())) }
            .standardParticipants
            .firstOrNull { it.participantId == participantId }
            ?.occupation

    private companion object {
        val OUTCOME_UNKNOWN: () -> Nothing = { throw EntryOutcomeUnknown() }
    }
}

/** 유저 서비스가 409로 답한 것을 재입장 판정으로 넘기는 내부 신호다. 밖으로 나가지 않는다. */
internal class AlreadyEnteredToday : RuntimeException(null, null, false, false)

/** 입장 기록 호출이 타임아웃처럼 결과를 모르게 끝난 것을 확인 조회로 넘기는 내부 신호다(#287). 밖으로 나가지 않는다. */
internal class EntryOutcomeUnknown : RuntimeException(null, null, false, false)
