package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.RecordWalkInReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.WalkInCountsResDto
import team.startup.expo.domain.attendance.service.ScannerName
import team.startup.expo.domain.attendance.service.WalkInService
import team.startup.expo.global.swagger.ApiError
import java.time.LocalDate

/** 입구 스캐너(관리자 계정)가 QR 없는 방문객을 개인정보 없이 센다(#118). 관리자(`ROLE_ADMIN`) 전용이다. */
@RestController
@RequestMapping("/walk-in-counts")
class WalkInController(
    private val walkInService: WalkInService,
) {
    @Operation(
        summary = "간편입장 +1 / 취소",
        description =
            "QR 없는 방문객을 구분(성인, 어린이·학생)별로 세거나(`action` `ADD`) 잘못 누른 것을 되돌린다(`UNDO`, 합계는 0 아래로 내려가지 않는다). " +
                "`requestId`를 보내면 같은 요청의 재전송이 두 번 세지지 않는다. 응답은 오늘 구분별 합계다.",
    )
    @ApiError(400, "요청 값이 올바르지 않거나 박람회가 진행 중이 아니다. 합계가 0인데 취소를 요청한 경우도 같다.")
    @ApiError(401, "관리자 역할 헤더(`X-User-Id`, `X-User-Role`)가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(404, "박람회를 찾지 못했다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @PostMapping("/{expoId}")
    fun record(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: RecordWalkInReqDto,
        @RequestHeader(name = ScannerName.HEADER, required = false) scannerName: String?,
    ): WalkInCountsResDto = walkInService.record(expoId, reqDto, scannerName)

    @Operation(summary = "간편입장 합계 조회", description = "날짜(없으면 오늘)의 구분별 간편입장 합계다. 관리자 전용이다.")
    @ApiError(400, "박람회 ID 형식(소문자 UUID)이나 날짜 형식이 올바르지 않다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}")
    fun get(
        @PathVariable expoId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    ): WalkInCountsResDto = walkInService.get(expoId, date)
}
