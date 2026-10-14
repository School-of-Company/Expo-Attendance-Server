package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(
    description =
        "박람회 하루의 입장 처리 건수. 등록 QR(참가자·연수자)과 종이 QR은 이 서비스의 입장 로그(퇴장 포함), 간편입장은 간편입장 카운터에서 센다. " +
            "입장은 QR당 하루 한 번이라 입장 수는 서로 다른 입장자 수와 같다. **입장 로그가 생기기 전의 입장은 포함되지 않는다.**",
)
data class EntrySummaryResDto(
    val expoId: String,
    @field:Schema(description = "집계한 날짜. 박람회 전체 기간(`all=true`)이면 비어 있다", nullable = true)
    val date: LocalDate?,
    @field:Schema(description = "집계한 시각(화면의 갱신 시각)")
    val generatedAt: LocalDateTime,
    val registered: RegisteredEntrySummaryResDto,
    @field:Schema(description = "종이 QR 구분별(`ADULT`, `CHILD` 등). 입장이 없는 구분은 나오지 않는다")
    val paper: List<PaperEntryCountResDto>,
    val walkIn: WalkInCountsResDto,
    val totals: EntryTotalsResDto,
    @field:Schema(
        description = "조회한 날짜와 상관없이 지금부터 10분 전까지의 입장 수(등록 QR + 종이 QR + 간편입장 순증감)",
        example = "18",
    )
    val recentEntered10m: Long,
    @field:Schema(
        description =
            "기간 안에서 서로 다른 QR 수(등록 QR + 종이 QR, 간편입장 제외). 하루 집계에서는 QR 입장 수와 같고, 전체 기간에서는 같은 QR의 다른 날 입장을 한 번만 센다",
        example = "61",
    )
    val uniqueQr: Long,
)

@Schema(description = "사전등록 QR 입장")
data class RegisteredEntrySummaryResDto(
    @field:Schema(description = "일반 참가자 입장 수", example = "7")
    val standard: Long,
    @field:Schema(description = "연수자 입장 수", example = "0")
    val trainee: Long,
)

data class PaperEntryCountResDto(
    @field:Schema(description = "종이 QR 구분", example = "ADULT")
    val category: String?,
    @field:Schema(description = "입장 수", example = "12")
    val entered: Long,
    @field:Schema(description = "이 중 퇴장한 수", example = "3")
    val exited: Long,
)

@Schema(description = "전체 합계")
data class EntryTotalsResDto(
    @field:Schema(description = "등록 QR + 종이 QR + 간편입장의 입장 합계", example = "73")
    val entered: Long,
    @field:Schema(description = "퇴장한 수(QR로 입장한 사람만 센다. 간편입장은 퇴장을 세지 않는다)", example = "10")
    val exited: Long,
    @field:Schema(description = "QR로 입장했고 아직 퇴장하지 않은 수(등록 QR + 종이 QR − 퇴장). 간편입장은 포함하지 않는다", example = "25")
    val stillInside: Long,
)
