package team.startup.expo.domain.qr.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.EntryStatus

@Schema(description = "종이 QR 입구 스캔 결과")
data class ScanQrTokenResDto(
    @field:Schema(description = "입장 상태. 오늘 처음이면 `FIRST`, 퇴장한 뒤 다시 입장하면 `RE_ENTRY`", example = "FIRST")
    val entryStatus: EntryStatus,
    @field:Schema(description = "오늘 최초 입장 시각(`HH:mm`). 재입장이어도 처음 입장한 시각이다", example = "10:05", nullable = true)
    val firstEnteredTime: String?,
)
