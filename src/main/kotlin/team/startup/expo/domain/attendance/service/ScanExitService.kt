package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanExitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanExitResDto

interface ScanExitService {
    /**
     * 오늘 입장한 사람의 퇴장 시각을 기록한다(#120). 입장 기록은 입장 로그에서 찾으므로, 입장 로그가 생기기 전의 입장이나 입장하지 않은 사람은
     * 400이고, 이미 퇴장했으면 다시 입장하기 전까지 400이다. 퇴장 뒤 같은 날 입장 스캔은 재입장으로 처리된다(#148).
     */
    fun scan(
        expoId: String,
        reqDto: ScanExitReqDto,
        scannerName: String? = null,
    ): ScanExitResDto
}
