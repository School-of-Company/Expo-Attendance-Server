package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto

interface ScanStandardProgramService {
    fun scan(
        programId: Long,
        reqDto: ScanStandardProgramReqDto,
    )
}
