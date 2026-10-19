package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "시간대별 입·퇴장 건수. 기록이 있는 시간만 나오므로 빈 시간은 0으로 채워 그린다")
data class HourlyEntryResDto(
    val expoId: String,
    @field:Schema(description = "집계한 날짜. 박람회 전체 기간이면 비어 있다", nullable = true)
    val date: LocalDate?,
    @field:Schema(description = "날짜·시간 순서")
    val buckets: List<HourlyEntryBucketResDto>,
)

data class HourlyEntryBucketResDto(
    val date: LocalDate,
    @field:Schema(description = "시(0~23, Asia/Seoul)", example = "14")
    val hour: Int,
    @field:Schema(description = "이 시간의 입장 수(등록 QR + 종이 QR + 간편입장 순증감)", example = "35")
    val entered: Long,
    @field:Schema(
        description =
            "이 시간의 퇴장 스캔 횟수(QR로 입장한 사람만 센다). 퇴장했다가 재입장한 사람이 다시 퇴장하면 또 센다. 요약의 `exited`와 같은 기준이다",
        example = "8",
    )
    val exited: Long,
)

@Schema(description = "최근 입·퇴장 기록(최신순)")
data class RecentEntryRecordsResDto(
    val expoId: String,
    @field:Schema(description = "조회한 날짜. 박람회 전체 기간이면 비어 있다", nullable = true)
    val date: LocalDate?,
    val records: List<EntryRecordResDto>,
)

data class EntryRecordResDto(
    @field:Schema(description = "기록된 시각")
    val at: LocalDateTime,
    @field:Schema(description = "`ENTER` 입장, `EXIT` 퇴장, `CANCEL` 간편입장 취소")
    val type: EntryRecordType,
    @field:Schema(description = "입장 방식")
    val method: EntryMethod,
    @field:Schema(
        description = "종이 QR이면 `ADULT`·`CHILD` 등, 간편입장이면 참가 유형 8종(#149 이전 기록은 `ADULT`·`STUDENT`). 그 밖에는 비어 있다",
        nullable = true,
    )
    val category: String?,
    @field:Schema(description = "참가자·연수자 이름. 등록 QR이고 유저 서비스가 이름을 돌려줄 때만 있다(개인정보라 이 서비스는 저장하지 않는다)", nullable = true)
    val name: String?,
    @field:Schema(description = "처리한 스캐너(기기) 이름. `X-Scanner-Name`을 보내지 않았으면 비어 있다", nullable = true)
    val scanner: String?,
)

enum class EntryRecordType { ENTER, EXIT, CANCEL }

enum class EntryMethod {
    /** 사전등록 QR(일반 참가자). */
    REGISTERED_STANDARD,

    /** 사전등록 QR(연수자). */
    REGISTERED_TRAINEE,

    /** 현장 종이 QR. */
    PAPER_QR,

    /** QR 없는 간편입장. */
    WALK_IN,
}

@Schema(description = "스캐너별 입장 처리 건수(많은 순). `X-Scanner-Name`을 보내지 않은 처리는 포함하지 않는다")
data class ScannerEntryCountsResDto(
    val expoId: String,
    @field:Schema(description = "집계한 날짜. 박람회 전체 기간이면 비어 있다", nullable = true)
    val date: LocalDate?,
    val scanners: List<ScannerEntryCountResDto>,
)

data class ScannerEntryCountResDto(
    @field:Schema(description = "스캐너(기기) 이름", example = "입구-1")
    val scanner: String,
    @field:Schema(description = "입장 처리 수(간편입장은 순증감)", example = "120")
    val entered: Long,
    @field:Schema(
        description = "퇴장 스캔 횟수. 재입장한 사람이 다시 퇴장하면 또 센다. 요약의 `exited`와 같은 기준이다",
        example = "30",
    )
    val exited: Long,
)
