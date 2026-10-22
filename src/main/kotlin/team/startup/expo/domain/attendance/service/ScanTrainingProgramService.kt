package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto

interface ScanTrainingProgramService {
    fun scan(
        programId: Long,
        reqDto: ScanTrainingProgramReqDto,
    ): ProgramScanResDto
}
