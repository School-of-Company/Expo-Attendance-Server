package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryListReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryListItemResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryListResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryListService
import team.startup.expo.domain.attendance.support.EntryBadge
import team.startup.expo.domain.attendance.support.NativeRows
import team.startup.expo.domain.attendance.support.PhoneNumberMask
import team.startup.expo.domain.attendance.support.TimeFormat
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.EntryCardsReqDto
import team.startup.expo.global.client.user.UserClient
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 이 서비스는 이름·연락처를 저장하지 않으므로, 입장 로그 한 페이지를 읽은 뒤 그 페이지의 사람만 유저 서비스에서 한 번에 가져온다.
 * 유저 서비스가 응답하지 못하면 503이다(이름 없는 표는 쓸모가 없어, 화면이 이전 표를 유지하고 다시 시도하게 한다). 입장 뒤 지워진 참가자는
 * 유저 서비스가 결과에서 빼므로 그 행은 ID·유형·입장 시각만 담는다.
 */
@Service
class EntryListServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) : EntryListService {
    override fun list(
        expoId: String,
        reqDto: EntryListReqDto,
    ): EntryListResDto {
        val date = reqDto.date ?: LocalDate.now(clock)
        val total = entryLogRepository.countRegisteredEntries(expoId, date)
        val rows =
            entryLogRepository
                .findRegisteredEntries(expoId, date, reqDto.size, reqDto.page.toLong() * reqDto.size)
                .map { Row.of(it) }
        val entries = if (rows.isEmpty()) emptyList() else toItems(expoId, rows)
        return EntryListResDto(expoId, date, reqDto.page, reqDto.size, total, entries)
    }

    private fun toItems(
        expoId: String,
        rows: List<Row>,
    ): List<EntryListItemResDto> {
        val cards =
            userCircuitBreaker.callService("유저") {
                userClient.getEntryCards(
                    EntryCardsReqDto(
                        expoId = expoId,
                        standardParticipantIds = rows.filter { it.type == EntrySubjectType.STANDARD }.map { it.id },
                        traineeIds = rows.filter { it.type == EntrySubjectType.TRAINEE }.map { it.id },
                    ),
                )
            }
        val participants = cards.standardParticipants.associateBy { it.participantId }
        val trainees = cards.trainees.associateBy { it.traineeId }
        return rows.map { row ->
            val card =
                when (row.type) {
                    EntrySubjectType.TRAINEE ->
                        trainees[row.id]?.let {
                            Card(it.name, it.phoneNumber, it.personalInformationStatus, EntryBadge.of(true, null, it.name, it.school))
                        }

                    else ->
                        participants[row.id]?.let {
                            val badge = EntryBadge.of(false, it.occupation, it.name, it.school)
                            Card(it.name, it.phoneNumber, it.personalInformationStatus, badge)
                        }
                }
            EntryListItemResDto(
                id = row.id,
                participationType = row.type.name,
                name = card?.name,
                phoneNumber = PhoneNumberMask.mask(card?.phoneNumber),
                personalInformationStatus = card?.personalInformationStatus,
                badge = card?.badge,
                firstEnteredTime = row.enteredAt.format(TimeFormat.HOUR_MINUTE),
                inside = row.exitedAt == null,
            )
        }
    }

    /** 유저 서비스 명함 중 목록 한 행에 필요한 값. 일반 참가자와 연수자의 명함 타입이 달라 여기서 맞춘다. */
    private data class Card(
        val name: String,
        val phoneNumber: String?,
        val personalInformationStatus: Boolean,
        val badge: BadgeResDto?,
    )

    /** `findRegisteredEntries`의 한 행. 등록 QR만 읽으므로 대상 ID는 참가자(연수자) ID다. */
    private data class Row(
        val type: EntrySubjectType,
        val id: Long,
        val enteredAt: LocalDateTime,
        val exitedAt: LocalDateTime?,
    ) {
        companion object {
            fun of(columns: Array<Any?>) =
                Row(
                    type = EntrySubjectType.valueOf(columns[0] as String),
                    id = (columns[1] as String).toLong(),
                    enteredAt = NativeRows.dateTimeOrNull(columns[2])!!,
                    exitedAt = NativeRows.dateTimeOrNull(columns[3]),
                )
        }
    }
}
