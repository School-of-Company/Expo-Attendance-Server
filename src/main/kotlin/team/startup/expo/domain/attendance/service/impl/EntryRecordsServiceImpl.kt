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
import team.startup.expo.domain.attendance.repository.EntryEventRow
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.WalkInLogRepository
import team.startup.expo.domain.attendance.service.EntryRecordsService
import team.startup.expo.domain.attendance.support.EntryRecordCursor
import team.startup.expo.domain.attendance.support.EntryScope
import team.startup.expo.domain.attendance.support.ExpoIdFormat
import team.startup.expo.domain.attendance.support.NativeRows
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate

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
        ExpoIdFormat.requireValid(expoId)
        val (from, to) = resolveRange(reqDto)
        val cursor = reqDto.cursor?.let { EntryRecordCursor.decode(it) }
        if (cursor != null && reqDto.page != 0) throw ExpectedException(HttpStatus.BAD_REQUEST, "cursor와 page는 함께 보낼 수 없습니다.")
        // 끝까지 읽는 호출자가 페이지마다 범위 전체를 다시 세지 않게, 커서로 이어 읽을 때는 세지 않는다(#250)
        val total = if (cursor == null) entryLogRepository.countEvents(expoId, from, to) else null
        entryLogRepository.planWithActualValues()
        // 한 건 더 읽어 다음 페이지가 있는지 안다. 그래서 마지막 페이지에는 nextCursor가 없다
        val rows =
            if (total == 0L) {
                emptyList()
            } else {
                val (afterDate, afterAt, afterId) = cursor ?: EntryRecordCursor.before(from)
                val offset = if (cursor == null) reqDto.page.toLong() * reqDto.size else 0L
                entryLogRepository.findEvents(expoId, from, to, afterDate, afterAt, afterId, reqDto.size + 1, offset)
            }
        val page = rows.take(reqDto.size)
        val nextCursor =
            if (rows.size > reqDto.size) {
                page.last().let { EntryRecordCursor(it.attendanceDate, it.occurredAt, it.id).encode() }
            } else {
                null
            }
        val records = page.map { toRecord(it) }
        val walkIn =
            if (reqDto.page == 0 && cursor == null) {
                walkInLogRepository
                    .sumByDateAndCategory(expoId, from, to)
                    .map { EntryRecordWalkInResDto(it[0] as LocalDate, it[1] as String, NativeRows.long(it[2])) }
                    .filter { it.count != 0L }
            } else {
                emptyList()
            }
        return EntryRecordsResDto(expoId, reqDto.from, reqDto.to, reqDto.page, reqDto.size, total, records, walkIn, nextCursor)
    }

    private fun resolveRange(reqDto: EntryRecordsReqDto): Pair<LocalDate, LocalDate> {
        val from = reqDto.from
        val to = reqDto.to
        if ((from == null) != (to == null)) throw ExpectedException(HttpStatus.BAD_REQUEST, "from과 to는 함께 보내야 합니다.")
        if (from == null || to == null) return EntryScope.EARLIEST to EntryScope.LATEST
        if (from.isAfter(to)) throw ExpectedException(HttpStatus.BAD_REQUEST, "from은 to보다 늦을 수 없습니다.")
        return from to to
    }

    private fun toRecord(row: EntryEventRow): EntryRecordItemResDto {
        val subjectType = EntrySubjectType.valueOf(row.subjectType)
        val paper = subjectType == EntrySubjectType.PAPER_QR
        return EntryRecordItemResDto(
            occurredAt = row.occurredAt,
            eventType = EntryEventType.valueOf(row.eventType),
            subjectType = subjectType,
            // 종이 QR의 subject_id는 토큰이라 응답에 싣지 않는다
            subjectId = if (paper) null else row.subjectId.toLongOrNull(),
            category = if (paper) row.category else null,
            paperCode = if (paper) row.paperCode else null,
            scanner = row.scanner,
        )
    }
}
