package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.CancellationStatusResDto

interface GetCancellationStatusService {
    /**
     * 신청 서비스가 신청별 취소 가능 여부를 계산하는 데 쓰는 읽기 전용 시점 조회다. 아무것도 기록하거나 잠그지 않는다. 신원·소유 확인과
     * 입장 여부는 유저 서비스, 프로그램 출석과 취소 보호 상태는 이 서비스에서 읽는다. 유저 서비스가 모르는 쌍은 404다.
     */
    fun get(
        expoId: String,
        participantId: Long,
        reqDto: GetCancellationStatusReqDto,
    ): CancellationStatusResDto
}
