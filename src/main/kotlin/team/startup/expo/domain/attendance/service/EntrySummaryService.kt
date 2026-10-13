package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.EntrySummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntrySummaryResDto

interface EntrySummaryService {
    /** 박람회 하루의 입장 처리 건수를 구분별로 모아 돌려준다(#119). 읽기 전용이고 조회 중 새 입장은 그 시점의 값이다. */
    fun get(
        expoId: String,
        reqDto: EntrySummaryReqDto,
    ): EntrySummaryResDto
}
