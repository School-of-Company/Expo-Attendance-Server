package team.startup.expo.domain.attendance.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ProgramAttendanceSummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramAttendanceSummaryResDto
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.GetProgramAttendanceSummaryService
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class GetProgramAttendanceSummaryServiceImpl(
    private val standardRepository: StandardProgramAttendanceRepository,
    private val trainingRepository: TrainingProgramAttendanceRepository,
) : GetProgramAttendanceSummaryService {
    @Transactional(readOnly = true)
    override fun summarize(reqDto: ProgramAttendanceSummaryReqDto): List<ProgramAttendanceSummaryResDto> {
        val refs = reqDto.programs.distinctBy { it.programType to it.programId }
        val standard = rows(refs.filter { it.programType == ProgramType.STANDARD }.map { it.programId }, standardRepository::summarize)
        val training = rows(refs.filter { it.programType == ProgramType.TRAINING }.map { it.programId }, trainingRepository::summarize)
        return refs.map { ref ->
            val row = (if (ref.programType == ProgramType.STANDARD) standard else training)[ref.programId]
            ProgramAttendanceSummaryResDto(
                programType = ref.programType,
                programId = ref.programId,
                attendeeCount = row?.count ?: 0,
                firstEntryTime = row?.first?.format(TIME_FORMAT),
                lastEntryTime = row?.last?.format(TIME_FORMAT),
            )
        }
    }

    private class Row(
        val count: Long,
        val first: LocalTime,
        val last: LocalTime,
    )

    private fun rows(
        programIds: List<Long>,
        query: (Collection<Long>) -> List<Array<Any>>,
    ): Map<Long, Row> {
        if (programIds.isEmpty()) return emptyMap()
        return query(programIds).associate { Pair(it[0] as Long, Row(it[1] as Long, it[2] as LocalTime, it[3] as LocalTime)) }
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
