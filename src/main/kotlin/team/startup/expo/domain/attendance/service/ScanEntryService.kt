package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto

interface ScanEntryService {
    fun scan(
        expoId: String,
        reqDto: ScanEntryReqDto,
        scannerId: String? = null,
    ): ScanEntryResDto
}
