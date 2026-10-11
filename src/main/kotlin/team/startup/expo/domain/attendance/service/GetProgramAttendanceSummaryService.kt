package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.ProgramAttendanceSummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ProgramAttendanceSummaryResDto

interface GetProgramAttendanceSummaryService {
    /**
     * 요청한 프로그램마다 출석 인원과 입실 시각 범위를 요청 순서대로(중복은 한 번만) 돌려준다. 이 서비스는 프로그램이 어느 박람회 소속인지
     * 모르므로 소속 확인은 부르는 쪽 몫이다. 저장소 장애는 0으로 바꾸지 않고 오류로 그대로 나간다.
     */
    fun summarize(reqDto: ProgramAttendanceSummaryReqDto): List<ProgramAttendanceSummaryResDto>
}
