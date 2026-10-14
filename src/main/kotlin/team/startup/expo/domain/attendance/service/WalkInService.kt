package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.RecordWalkInReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.WalkInCountsResDto
import java.time.LocalDate

interface WalkInService {
    /**
     * 오늘 간편입장을 한 번 더하거나(`ADD`) 되돌린다(`UNDO`)(#118). 박람회가 진행 중이어야 하고(박람회 서비스가 응답하지 못하면 입구를 막지 않으려고
     * 기간 확인 없이 센다), 합계는 0 아래로 내려가지 않는다. `requestId`가 같은 재전송은 한 번만 센다. 같은 구분의 동시 증감은 락으로 한 줄로 세운다.
     */
    fun record(
        expoId: String,
        reqDto: RecordWalkInReqDto,
        scannerName: String? = null,
    ): WalkInCountsResDto

    /** 기간(`from`~`to`) 전체의 구분별 합계. 응답의 `date`는 비어 있다(#131). */
    fun getBetween(
        expoId: String,
        from: LocalDate,
        to: LocalDate,
    ): WalkInCountsResDto

    /** 날짜별 구분별 합계. 날짜가 없으면 오늘이다. */
    fun get(
        expoId: String,
        date: LocalDate?,
    ): WalkInCountsResDto
}
