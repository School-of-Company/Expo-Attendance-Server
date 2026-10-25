package team.startup.expo.domain.attendance.service.impl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntrySummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.RecentEntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ScannerEntryCountsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryMethod
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordType
import team.startup.expo.domain.attendance.presentation.dto.response.HourlyEntryBucketResDto
import team.startup.expo.domain.attendance.presentation.dto.response.HourlyEntryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.RecentEntryRecordsResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScannerEntryCountResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScannerEntryCountsResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.service.EntryStatsService
import team.startup.expo.domain.attendance.support.EntryScope
import team.startup.expo.global.client.callService
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import java.sql.Date
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class EntryStatsServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val userClient: UserClient,
    @Qualifier("userCircuitBreaker") private val userCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) : EntryStatsService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun hourly(
        expoId: String,
        reqDto: EntrySummaryReqDto,
    ): HourlyEntryResDto {
        val scope = EntryScope.of(reqDto.date, reqDto.all, clock)
        val buckets =
            entryLogRepository.hourly(expoId, scope.from, scope.to).map {
                HourlyEntryBucketResDto(toDate(it[0]), (it[1] as Number).toInt(), (it[2] as Number).toLong(), (it[3] as Number).toLong())
            }
        return HourlyEntryResDto(expoId, scope.date, buckets)
    }

    override fun recent(
        expoId: String,
        reqDto: RecentEntryRecordsReqDto,
    ): RecentEntryRecordsResDto {
        val scope = EntryScope.of(reqDto.date, reqDto.all, clock)
        val rows = entryLogRepository.recent(expoId, scope.from, scope.to, reqDto.limit).map { Row.of(it) }
        val names = lookupNames(expoId, rows)
        val records =
            rows.map {
                EntryRecordResDto(
                    at = it.at,
                    type = it.type,
                    method = it.method,
                    category = it.category,
                    name = it.subjectId?.let { id -> names[it.method to id] },
                    scanner = it.scanner,
                )
            }
        return RecentEntryRecordsResDto(expoId, scope.date, records)
    }

    override fun scanners(
        expoId: String,
        reqDto: ScannerEntryCountsReqDto,
    ): ScannerEntryCountsResDto {
        val scope = EntryScope.of(reqDto.date, reqDto.all, clock)
        val scanners =
            entryLogRepository.scanners(expoId, scope.from, scope.to, reqDto.limit).map {
                ScannerEntryCountResDto(it[0] as String, (it[1] as Number).toLong(), (it[2] as Number).toLong())
            }
        return ScannerEntryCountsResDto(expoId, scope.date, scanners)
    }

    /**
     * 등록 QR 입·퇴장의 이름을 유저 서비스에서 한 번에 가져온다. 이름은 부가 정보라, 유저 서비스가 응답하지 못하거나 없는 참가자가 있어
     * 일괄 조회가 실패하면 이름 없이 돌려준다(현황 화면이 이름 때문에 멈추지 않게 한다).
     */
    private fun lookupNames(
        expoId: String,
        rows: List<Row>,
    ): Map<Pair<EntryMethod, String>, String> {
        val names = mutableMapOf<Pair<EntryMethod, String>, String>()
        val standardIds =
            rows
                .filter { it.method == EntryMethod.REGISTERED_STANDARD }
                .mapNotNull { it.subjectId?.toLongOrNull() }
                .distinct()
        val traineeIds = rows.filter { it.method == EntryMethod.REGISTERED_TRAINEE }.mapNotNull { it.subjectId?.toLongOrNull() }.distinct()
        if (standardIds.isNotEmpty()) {
            fetch("일반 참가자") {
                userCircuitBreaker
                    .callService("유저") { userClient.getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, standardIds)) }
                    .forEach { names[EntryMethod.REGISTERED_STANDARD to it.participantId.toString()] = it.name }
            }
        }
        if (traineeIds.isNotEmpty()) {
            fetch("연수자") {
                userCircuitBreaker
                    .callService("유저") { userClient.getTraineeNames(TraineeNamesReqDto(expoId, traineeIds)) }
                    .forEach { names[EntryMethod.REGISTERED_TRAINEE to it.traineeId.toString()] = it.name }
            }
        }
        return names
    }

    private fun fetch(
        what: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            logger.warn("최근 입·퇴장 기록의 {} 이름을 가져오지 못해 이름 없이 돌려줍니다: {}", what, e.javaClass.simpleName)
        }
    }

    /** `recent` 쿼리의 한 행. 종이 QR의 입장 대상 ID(토큰)는 응답에 싣지 않으려고 여기서 비운다. */
    private data class Row(
        val at: LocalDateTime,
        val type: EntryRecordType,
        val method: EntryMethod,
        val category: String?,
        val subjectId: String?,
        val scanner: String?,
    ) {
        companion object {
            fun of(columns: Array<Any?>): Row {
                val method =
                    when (columns[2] as String) {
                        EntrySubjectType.STANDARD.name -> EntryMethod.REGISTERED_STANDARD
                        EntrySubjectType.TRAINEE.name -> EntryMethod.REGISTERED_TRAINEE
                        EntrySubjectType.PAPER_QR.name -> EntryMethod.PAPER_QR
                        else -> EntryMethod.WALK_IN
                    }
                return Row(
                    at = toDateTime(columns[0]),
                    type = EntryRecordType.valueOf(columns[1] as String),
                    method = method,
                    category = columns[3] as String?,
                    subjectId = (columns[4] as String?)?.takeIf { method.isRegistered() },
                    scanner = columns[5] as String?,
                )
            }

            private fun EntryMethod.isRegistered() = this == EntryMethod.REGISTERED_STANDARD || this == EntryMethod.REGISTERED_TRAINEE
        }
    }

    private companion object {
        fun toDate(value: Any?): LocalDate =
            when (value) {
                is LocalDate -> value
                is Date -> value.toLocalDate()
                else -> throw IllegalStateException("날짜 열의 타입을 알 수 없습니다: ${value?.javaClass}")
            }

        fun toDateTime(value: Any?): LocalDateTime =
            when (value) {
                is LocalDateTime -> value
                is Timestamp -> value.toLocalDateTime()
                else -> throw IllegalStateException("시각 열의 타입을 알 수 없습니다: ${value?.javaClass}")
            }
    }
}
