package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ScanExitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanExitResDto

interface ScanExitService {
    /**
     * 오늘 입장한 사람의 퇴장 시각을 기록한다(#120). 입장 기록은 입장 로그에서 찾으므로, 입장 로그가 생기기 전의 입장이나 입장하지 않은 사람은
     * 400이고, 이미 퇴장했으면 400이다. 재입장은 아직 허용하지 않는다(입장은 하루 한 번).
     */
    fun scan(
        expoId: String,
        reqDto: ScanExitReqDto,
    ): ScanExitResDto
}
