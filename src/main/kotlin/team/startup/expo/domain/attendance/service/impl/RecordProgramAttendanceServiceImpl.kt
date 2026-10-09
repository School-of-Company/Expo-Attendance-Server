package team.startup.expo.domain.attendance.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.repository.DeletedProgramRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import java.time.LocalDate
import java.time.LocalTime

/** 입실은 `INSERT ... ON CONFLICT DO NOTHING`이라 동시 스캔에도 한 번만 기록된다. 퇴실은 기록하지 않는다. */
@Service
class RecordProgramAttendanceServiceImpl(
    private val deletedProgramRepository: DeletedProgramRepository,
    private val standardRepository: StandardProgramAttendanceRepository,
    private val trainingRepository: TrainingProgramAttendanceRepository,
) : RecordProgramAttendanceService {
    @Transactional
    override fun record(
        type: ProgramType,
        programId: Long,
        personId: Long,
        attendanceDate: LocalDate,
        time: LocalTime,
    ): ProgramAttendanceResult {
        val programKey = type.keyOf(programId)
        deletedProgramRepository.lockProgram(programKey)
        if (deletedProgramRepository.existsById(programKey)) return ProgramAttendanceResult.PROGRAM_DELETED

        val entered =
            when (type) {
                ProgramType.STANDARD -> standardRepository.insertEntryIfAbsent(programId, personId, attendanceDate, time)
                ProgramType.TRAINING -> trainingRepository.insertEntryIfAbsent(programId, personId, attendanceDate, time)
            }
        return if (entered == 1) ProgramAttendanceResult.ENTERED else ProgramAttendanceResult.ALREADY_ENTERED
    }
}
