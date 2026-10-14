package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.EntrySummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.RecentEntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ScannerEntryCountsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.HourlyEntryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.RecentEntryRecordsResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScannerEntryCountsResDto

/** 실시간 입·퇴장 현황 화면용 조회(#131). 모두 읽기 전용이다. */
interface EntryStatsService {
    /** 날짜·시간별 입·퇴장 건수. */
    fun hourly(
        expoId: String,
        reqDto: EntrySummaryReqDto,
    ): HourlyEntryResDto

    /** 최신 입·퇴장 기록. 등록 QR의 이름은 유저 서비스에서 가져오며, 가져오지 못해도 기록은 이름 없이 돌려준다. */
    fun recent(
        expoId: String,
        reqDto: RecentEntryRecordsReqDto,
    ): RecentEntryRecordsResDto

    /** 스캐너별 입장·퇴장 처리 건수 상위 N개. */
    fun scanners(
        expoId: String,
        reqDto: ScannerEntryCountsReqDto,
    ): ScannerEntryCountsResDto
}
