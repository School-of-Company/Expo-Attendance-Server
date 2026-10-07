package team.startup.expo.domain.qr.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.qr.presentation.dto.ResolveQrTokenReqDto
import team.startup.expo.domain.qr.presentation.dto.ResolveQrTokenResDto
import team.startup.expo.domain.qr.service.ResolveQrTokenService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 토큰은 "이 종이를 받았다"는 증명이라 URL이나 쿼리에 남지 않도록 요청 본문으로만 받는다.
 */
@RestController
@RequestMapping("/internal/qr-tokens")
class InternalQrTokenController(
    private val resolveQrTokenService: ResolveQrTokenService,
) {
    @Operation(summary = "입장이 확인된 QR 토큰 조회", description = "입장이 확인된 토큰이면 박람회 id를 돌려줍니다. 없는 토큰이거나 아직 입장하지 않은 토큰은 둘 다 404입니다.")
    @PostMapping("/resolve")
    fun resolve(
        @Valid @RequestBody reqDto: ResolveQrTokenReqDto,
    ): ResolveQrTokenResDto = resolveQrTokenService.execute(reqDto)
}
