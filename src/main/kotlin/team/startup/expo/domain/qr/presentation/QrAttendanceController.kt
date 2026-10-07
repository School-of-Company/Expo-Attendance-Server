package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.qr.presentation.dto.request.ScanQrTokenReqDto
import team.startup.expo.domain.qr.service.ScanQrTokenService

@RestController
@RequestMapping("/attendance/qr")
class QrAttendanceController(
    private val scanQrTokenService: ScanQrTokenService,
) {
    @Operation(summary = "종이 QR 입구 스캔", description = "토큰의 오늘 입장을 기록한다. 같은 날 두 번째 스캔은 400, 없는 토큰·다른 박람회 토큰은 404다.")
    @PatchMapping("/{expoId}")
    fun scan(
        @PathVariable expoId: String,
        @Valid @RequestBody reqDto: ScanQrTokenReqDto,
    ) = scanQrTokenService.scan(expoId, reqDto)
}
