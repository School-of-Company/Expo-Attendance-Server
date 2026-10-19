package team.startup.expo.domain.attendance.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.attendance.presentation.dto.request.EntryRecordsReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.EntryRecordsResDto
import team.startup.expo.domain.attendance.service.EntryRecordsService
import team.startup.expo.global.swagger.ApiError

/** Report 서버가 입장 기록 엑셀을 만들 때 부른다(#150). 이름·연락처는 싣지 않고 Report가 유저 서비스에서 따로 가져간다. */
@RestController
@RequestMapping("/internal/expos/{expoId}/entry-records")
class InternalEntryRecordsController(
    private val entryRecordsService: EntryRecordsService,
) {
    @Operation(
        summary = "입장 기록 목록 조회",
        description =
            "입장·재입장·퇴장 이력(`tb_entry_event`)을 발생 시각, 같으면 id 오름차순으로 페이지 단위로 돌려준다. " +
                "`from`·`to`(포함)가 없으면 박람회 전체 기간이다. 이름·연락처와 종이 QR 토큰은 싣지 않는다(종이 QR은 `category`와 `paperCode`). " +
                "`walkIn`은 같은 범위의 간편입장 순증감을 날짜·구분별로 합친 것으로 첫 페이지에만 채운다. 읽기 전용이다.",
    )
    @ApiError(400, "박람회 ID 형식이나 날짜 형식이 올바르지 않거나, `from`/`to` 중 하나만 보냈거나, `from`이 `to`보다 늦거나, `page`·`size`가 범위를 벗어났다.")
    @ApiError(503, "DB 일시 장애로 요청이 몰려 잠시 처리하지 못했다. 잠시 후 다시 시도한다.")
    @GetMapping
    fun get(
        @PathVariable expoId: String,
        @Valid @ParameterObject @ModelAttribute reqDto: EntryRecordsReqDto,
    ): EntryRecordsResDto = entryRecordsService.get(expoId, reqDto)
}
