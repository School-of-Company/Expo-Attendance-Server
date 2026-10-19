package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryEventType
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordItemResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordWalkInResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordsResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.WalkInLogRepository
import team.startup.expo.domain.attendance.service.EntryRecordsService
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class EntryRecordsServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val walkInLogRepository: WalkInLogRepository,
) : EntryRecordsService {
    @Transactional(readOnly = true)
    override fun get(
        expoId: String,
        reqDto: EntryRecordsReqDto,
    ): EntryRecordsResDto {
        if (!EXPO_ID_PATTERN.matches(expoId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.")
        val (from, to) = resolveRange(reqDto)
        val total = entryLogRepository.countEvents(expoId, from, to)
        val records =
            if (total == 0L) {
                emptyList()
            } else {
                entryLogRepository
                    .findEvents(expoId, from, to, reqDto.size, reqDto.page.toLong() * reqDto.size)
                    .map { toRecord(it) }
            }
        val walkIn =
            if (reqDto.page == 0) {
                walkInLogRepository
                    .sumByDateAndCategory(expoId, from, to)
                    .map { EntryRecordWalkInResDto(it[0] as LocalDate, it[1] as String, (it[2] as Number).toLong()) }
                    .filter { it.count != 0L }
            } else {
                emptyList()
            }
        return EntryRecordsResDto(expoId, reqDto.from, reqDto.to, reqDto.page, reqDto.size, total, records, walkIn)
    }

    private fun resolveRange(reqDto: EntryRecordsReqDto): Pair<LocalDate, LocalDate> {
        val from = reqDto.from
        val to = reqDto.to
        if ((from == null) != (to == null)) throw ExpectedException(HttpStatus.BAD_REQUEST, "from과 to는 함께 보내야 합니다.")
        if (from == null || to == null) return EARLIEST to LATEST
        if (from.isAfter(to)) throw ExpectedException(HttpStatus.BAD_REQUEST, "from은 to보다 늦을 수 없습니다.")
        return from to to
    }

    private fun toRecord(row: Array<Any?>): EntryRecordItemResDto {
        val subjectType = EntrySubjectType.valueOf(row[2] as String)
        val paper = subjectType == EntrySubjectType.PAPER_QR
        return EntryRecordItemResDto(
            occurredAt = row[0] as LocalDateTime,
            eventType = EntryEventType.valueOf(row[1] as String),
            subjectType = subjectType,
            // 종이 QR의 subject_id는 토큰이라 응답에 싣지 않는다
            subjectId = if (paper) null else (row[3] as String).toLongOrNull(),
            category = if (paper) row[4] as String? else null,
            paperCode = if (paper) row[5] as String? else null,
            scanner = row[6] as String?,
        )
    }

    private companion object {
        val EXPO_ID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)
        val LATEST: LocalDate = LocalDate.of(2999, 12, 31)
    }
}
