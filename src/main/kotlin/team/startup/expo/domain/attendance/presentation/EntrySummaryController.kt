package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.EntrySummaryReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.RecentEntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ScannerEntryCountsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntrySummaryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.HourlyEntryResDto
import team.startup.expo.domain.attendance.presentation.dto.response.RecentEntryRecordsResDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScannerEntryCountsResDto
import team.startup.expo.domain.attendance.service.EntryStatsService
import team.startup.expo.domain.attendance.service.EntrySummaryService
import team.startup.expo.global.swagger.ApiError

/** 입구 화면의 "오늘 입장 처리 건수"(#119). 관리자(`ROLE_ADMIN`) 전용이다. */
@RestController
@RequestMapping("/attendance/summary")
class EntrySummaryController(
    private val entrySummaryService: EntrySummaryService,
    private val entryStatsService: EntryStatsService,
) {
    @Operation(
        summary = "오늘 입장 처리 건수 조회",
        description =
            "박람회 하루(`date`, 없으면 오늘)나 전체 기간(`all=true`)의 입장 처리 건수를 등록 QR(일반 참가자·연수자), 종이 QR 구분별(입장·퇴장), 간편입장 구분별로 돌려준다. " +
                "최근 10분 입장 수(`recentEntered10m`)와 서로 다른 QR 수(`uniqueQr`)도 함께 준다. " +
                "입장은 QR당 하루 한 번이라 입장 수는 서로 다른 입장자 수와 같다. 입장 로그가 생기기 전의 입장은 포함되지 않는다. 읽기 전용이다.",
    )
    @ApiError(400, "박람회 ID 형식(소문자 UUID)이나 날짜 형식이 올바르지 않거나, `date`와 `all`을 함께 보냈다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}")
    fun get(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: EntrySummaryReqDto,
    ): EntrySummaryResDto = entrySummaryService.get(expoId, reqDto)

    @Operation(
        summary = "시간대별 입·퇴장 건수",
        description =
            "날짜·시간(0~23시)별 입장·퇴장 수를 돌려준다. 입장은 입장 시각, 퇴장은 퇴장 시각의 시간에 센다. 간편입장은 순증감을 입장으로 센다. " +
                "기록이 있는 시간만 나온다. 날짜가 없으면 오늘, `all=true`면 박람회 전체 기간이다. 읽기 전용이다.",
    )
    @ApiError(400, "박람회 ID나 날짜 형식이 올바르지 않거나, `date`와 `all`을 함께 보냈다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}/hourly")
    fun hourly(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: EntrySummaryReqDto,
    ): HourlyEntryResDto = entryStatsService.hourly(expoId, reqDto)

    @Operation(
        summary = "최근 입·퇴장 기록",
        description =
            "최신 입장·퇴장·간편입장 취소 기록을 최신순으로 `limit`건(최대 100, 기본 30) 돌려준다. 입장 방식, 종이 QR·간편입장 구분, 처리한 스캐너를 담는다. " +
                "등록 QR이면 유저 서비스에서 참가자 이름을 가져와 담지만, 가져오지 못하면 이름만 비운다. 날짜가 없으면 오늘, `all=true`면 박람회 전체 기간이다.",
    )
    @ApiError(400, "박람회 ID나 날짜 형식이 올바르지 않거나, `limit`이 범위를 벗어났거나, `date`와 `all`을 함께 보냈다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}/recent")
    fun recent(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: RecentEntryRecordsReqDto,
    ): RecentEntryRecordsResDto = entryStatsService.recent(expoId, reqDto)

    @Operation(
        summary = "스캐너별 입장 처리 건수",
        description =
            "스캔 요청의 `X-Scanner-Name` 헤더(기기별 이름, 최대 40자)로 모은 스캐너별 입장·퇴장 처리 수를 많은 순으로 `limit`개(최대 100, 기본 15) 돌려준다. " +
                "헤더를 보내지 않은 처리와 이 기능 이전의 기록은 세지 않는다. 날짜가 없으면 오늘, `all=true`면 박람회 전체 기간이다.",
    )
    @ApiError(400, "박람회 ID나 날짜 형식이 올바르지 않거나, `limit`이 범위를 벗어났거나, `date`와 `all`을 함께 보냈다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}/scanners")
    fun scanners(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: ScannerEntryCountsReqDto,
    ): ScannerEntryCountsResDto = entryStatsService.scanners(expoId, reqDto)
}
