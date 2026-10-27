package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryListReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryListItemResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryListResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryListService
import team.startup.expo.domain.attendance.support.EntryBadge
import team.startup.expo.domain.attendance.support.PhoneNumberMask
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.EntryCardsReqDto
import team.startup.expo.global.client.user.UserClient
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            val time = row.enteredAt.format(TIME)
            val inside = row.exitedAt == null
            when (row.type) {
                EntrySubjectType.TRAINEE -> {
                    val card = trainees[row.id]
                    EntryListItemResDto(
                        id = row.id,
                        participationType = row.type.name,
                        name = card?.name,
                        phoneNumber = PhoneNumberMask.mask(card?.phoneNumber),
                        personalInformationStatus = card?.personalInformationStatus,
                        badge = card?.let { EntryBadge.of(true, null, it.name, it.school) },
                        firstEnteredTime = time,
                        inside = inside,
                    )
                }

                else -> {
                    val card = participants[row.id]
                    EntryListItemResDto(
                        id = row.id,
                        participationType = row.type.name,
                        name = card?.name,
                        phoneNumber = PhoneNumberMask.mask(card?.phoneNumber),
                        personalInformationStatus = card?.personalInformationStatus,
                        badge = card?.let { EntryBadge.of(false, it.occupation, it.name, it.school) },
                        firstEnteredTime = time,
                        inside = inside,
                    )
                }
            }
        }
    }

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
                    enteredAt = toDateTime(columns[2])!!,
                    exitedAt = toDateTime(columns[3]),
                )

            private fun toDateTime(value: Any?): LocalDateTime? =
                when (value) {
                    null -> null
                    is LocalDateTime -> value
                    is Timestamp -> value.toLocalDateTime()
                    else -> throw IllegalStateException("시각 열의 타입을 알 수 없습니다: ${value.javaClass}")
                }
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
