package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.EntryListReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryListResDto

/** 입구 화면의 "입장 처리한 참가자" 표를 서버에서 다시 불러오는 조회(#237). 읽기 전용이다. */
interface EntryListService {
    fun list(
        expoId: String,
        reqDto: EntryListReqDto,
    ): EntryListResDto
}
