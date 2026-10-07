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
    @Operation(summary = "박람회 데이터 정리", description = "박람회 삭제 시 종이 QR 토큰과 입장 이벤트를 지운다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{expoId}")
    fun delete(
        @PathVariable expoId: String,
    ) = deleteExpoDataService.delete(expoId)
}
