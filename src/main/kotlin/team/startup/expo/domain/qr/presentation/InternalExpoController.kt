package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.qr.service.DeleteExpoDataService

@RestController
@RequestMapping("/internal/expos")
class InternalExpoController(
    private val deleteExpoDataService: DeleteExpoDataService,
) {
    @Operation(
        summary = "박람회 데이터 정리",
        description =
            "박람회 삭제 시 이 박람회의 종이 QR 토큰과 그 입장 기록, 사전등록 회차, 취소 보호와 중단 기록, 입장 로그·이벤트, " +
                "간편입장 기록, 날짜별 현장등록과 그 취소 보호, 입장 시도 기록을 지우고 삭제 기록을 남긴다. 여러 번 불러도 결과는 같다.",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{expoId}")
    fun delete(
        @PathVariable expoId: String,
    ) = deleteExpoDataService.delete(expoId)
}
