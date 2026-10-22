package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로그램 출석 스캔 결과")
data class ProgramScanResDto(
    @field:Schema(
        description = "이번 스캔의 결과. 입실을 기록했으면 `ENTERED`, 퇴실을 기록했으면 `LEFT`, 같은 분 안의 연속 스캔이거나 퇴실한 뒤라 바꾼 것이 없으면 `UNCHANGED`",
        example = "ENTERED",
    )
    val result: ProgramScanResult,
    @field:Schema(description = "참가자·연수자 이름. 유저 서비스에서 읽지 못하면 `null`이다", example = "홍길동", nullable = true)
    val name: String?,
    @field:Schema(description = "이번에 기록한 입실 또는 퇴실 시각(`HH:mm`). `UNCHANGED`면 `null`이다", example = "10:05", nullable = true)
    val time: String?,
) {
    override fun toString() = "ProgramScanResDto(result=$result)"
}

enum class ProgramScanResult { ENTERED, LEFT, UNCHANGED }
