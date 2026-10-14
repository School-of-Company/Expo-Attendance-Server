package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntrySummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntrySummaryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryTotalsResDto
import team.startup.expo.domain.attendance.presentation.dto.response.PaperEntryCountResDto
import team.startup.expo.domain.attendance.presentation.dto.response.RegisteredEntrySummaryResDto
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.WalkInLogRepository
import team.startup.expo.domain.attendance.service.EntryScope
import team.startup.expo.domain.attendance.service.EntrySummaryService
import team.startup.expo.domain.attendance.service.WalkInService
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDateTime

@Service
class EntrySummaryServiceImpl(
    private val entryLogRepository: EntryLogRepository,
    private val walkInService: WalkInService,
    private val walkInLogRepository: WalkInLogRepository,
    private val clock: Clock,
) : EntrySummaryService {
    override fun get(
        expoId: String,
        reqDto: EntrySummaryReqDto,
    ): EntrySummaryResDto {
        val scope = EntryScope.of(reqDto.date, reqDto.all, clock)
        val generatedAt = LocalDateTime.now(clock)
        val walkIn =
            if (scope.date != null) walkInService.get(expoId, scope.date) else walkInService.getBetween(expoId, scope.from, scope.to)
        val rows =
            entryLogRepository.summarize(expoId, scope.from, scope.to).map {
                Row(EntrySubjectType.valueOf(it[0] as String), it[1] as String?, (it[2] as Number).toLong(), (it[3] as Number).toLong())
            }

        fun entered(type: EntrySubjectType) = rows.filter { it.type == type }.sumOf { it.entered }

        val standard = entered(EntrySubjectType.STANDARD)
        val trainee = entered(EntrySubjectType.TRAINEE)
        val paper =
            rows
                .filter { it.type == EntrySubjectType.PAPER_QR }
                .map { PaperEntryCountResDto(it.category, it.entered, it.exited) }
                .sortedBy { it.category }

        val qrEntered = standard + trainee + paper.sumOf { it.entered }
        val exited = rows.sumOf { it.exited }
        return EntrySummaryResDto(
            expoId = expoId,
            date = scope.date,
            generatedAt = generatedAt,
            registered = RegisteredEntrySummaryResDto(standard, trainee),
            paper = paper,
            walkIn = walkIn,
            totals = EntryTotalsResDto(entered = qrEntered + walkIn.total, exited = exited, stillInside = qrEntered - exited),
            recentEntered10m = recentEntered(expoId, generatedAt),
            uniqueQr = entryLogRepository.countDistinctSubjects(expoId, scope.from, scope.to),
        )
    }

    /** 지금부터 10분 전까지의 QR 입장과 간편입장 순증감. 취소가 많아도 0 아래로 내려가지 않게 한다. */
    private fun recentEntered(
        expoId: String,
        now: LocalDateTime,
    ): Long {
        val since = now.minusMinutes(RECENT_MINUTES)
        return (entryLogRepository.countEnteredSince(expoId, since) + walkInLogRepository.sumDeltaSince(expoId, since)).coerceAtLeast(0)
    }

    private data class Row(
        val type: EntrySubjectType,
        val category: String?,
        val entered: Long,
        val exited: Long,
    )

    private companion object {
        const val RECENT_MINUTES = 10L
    }
}
