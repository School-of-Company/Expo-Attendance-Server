package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramScanResDto

interface ScanStandardProgramService {
    fun scan(
        programId: Long,
        reqDto: ScanStandardProgramReqDto,
    ): ProgramScanResDto
}
