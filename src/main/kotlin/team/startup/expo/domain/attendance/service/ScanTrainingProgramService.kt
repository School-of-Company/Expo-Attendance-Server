package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto

interface ScanTrainingProgramService {
    fun scan(
        programId: Long,
        reqDto: ScanTrainingProgramReqDto,
    )
}
