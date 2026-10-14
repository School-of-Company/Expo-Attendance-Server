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
import team.startup.expo.domain.attendance.presentation.dto.response.EntrySummaryResDto
import team.startup.expo.domain.attendance.service.EntrySummaryService
import team.startup.expo.global.swagger.ApiError

/** 입구 화면의 "오늘 입장 처리 건수"(#119). 관리자(`ROLE_ADMIN`) 전용이다. */
@RestController
@RequestMapping("/attendance/summary")
class EntrySummaryController(
    private val entrySummaryService: EntrySummaryService,
) {
    @Operation(
        summary = "오늘 입장 처리 건수 조회",
        description =
            "박람회 하루의 입장 처리 건수를 등록 QR(일반 참가자·연수자), 종이 QR 구분별(입장·퇴장), 간편입장 구분별로 돌려준다. " +
                "입장은 QR당 하루 한 번이라 입장 수는 서로 다른 입장자 수와 같다. 입장 로그가 생기기 전의 입장은 포함되지 않는다. 읽기 전용이다.",
    )
    @ApiError(400, "박람회 ID 형식(소문자 UUID)이나 날짜 형식이 올바르지 않다.")
    @ApiError(401, "관리자 역할 헤더가 없다.")
    @ApiError(403, "`X-User-Role`이 `ROLE_ADMIN`이 아니다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping("/{expoId}")
    fun get(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: EntrySummaryReqDto,
    ): EntrySummaryResDto = entrySummaryService.get(expoId, reqDto)
}
