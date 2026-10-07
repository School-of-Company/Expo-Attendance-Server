package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.response.StandardProgramAttendanceResDto
import team.startup.expo.domain.attendance.presentation.dto.response.TrainingProgramAttendanceResDto

interface GetProgramAttendancesService {
    fun getStandard(programId: Long): List<StandardProgramAttendanceResDto>

    fun getTraining(programId: Long): List<TrainingProgramAttendanceResDto>
}
