package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.EntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordsResDto

interface EntryRecordsService {
    /** 박람회의 입장·재입장·퇴장 이력을 범위·페이지로 돌려준다(#150). 읽기 전용이고 다른 서비스를 부르지 않는다. */
    fun get(
        expoId: String,
        reqDto: EntryRecordsReqDto,
    ): EntryRecordsResDto
}
