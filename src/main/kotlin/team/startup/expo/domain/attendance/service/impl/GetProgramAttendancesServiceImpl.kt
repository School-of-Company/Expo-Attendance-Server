package team.startup.expo.domain.attendance.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.presentation.dto.response.StandardProgramAttendanceResDto
import team.startup.expo.domain.attendance.presentation.dto.response.TrainingProgramAttendanceResDto
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.GetProgramAttendancesService
import team.startup.expo.domain.attendance.support.TimeFormat
import java.time.LocalTime

@Service
class GetProgramAttendancesServiceImpl(
    private val standardRepository: StandardProgramAttendanceRepository,
    private val trainingRepository: TrainingProgramAttendanceRepository,
) : GetProgramAttendancesService {
    @Transactional(readOnly = true)
    override fun getStandard(programId: Long): List<StandardProgramAttendanceResDto> =
        standardRepository.findAllByStandardProgramId(programId).map {
            StandardProgramAttendanceResDto(it.participantId, it.attendanceDate, it.entryTime.text(), it.leaveTime?.text())
        }

    @Transactional(readOnly = true)
    override fun getTraining(programId: Long): List<TrainingProgramAttendanceResDto> =
        trainingRepository.findAllByTrainingProgramId(programId).map {
            TrainingProgramAttendanceResDto(it.traineeId, it.attendanceDate, it.entryTime.text(), it.leaveTime?.text())
        }

    private fun LocalTime.text(): String = format(TimeFormat.HOUR_MINUTE)
}
