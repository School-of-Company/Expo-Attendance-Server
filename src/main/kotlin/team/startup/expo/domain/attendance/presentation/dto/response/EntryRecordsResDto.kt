package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "입장 기록 목록(Report 엑셀 원천). 이름·연락처와 종이 QR 토큰은 담지 않는다")
data class EntryRecordsResDto(
    val expoId: String,
    @field:Schema(description = "요청한 범위의 시작. 박람회 전체를 요청했으면 비어 있다", nullable = true)
    val from: LocalDate?,
    @field:Schema(description = "요청한 범위의 끝. 박람회 전체를 요청했으면 비어 있다", nullable = true)
    val to: LocalDate?,
    val page: Int,
    val size: Int,
    @field:Schema(description = "범위 안 전체 이벤트 수(페이지와 상관없음)")
    val totalElements: Long,
    @field:Schema(description = "발생 시각, 같으면 id 오름차순")
    val records: List<EntryRecordItemResDto>,
    @field:Schema(description = "범위 안 간편입장 순증감을 날짜·구분별로 합친 것. 첫 페이지(`page = 0`)에서만 채우고 나머지는 빈 배열. 합계가 0인 날짜·구분은 싣지 않는다")
    val walkIn: List<EntryRecordWalkInResDto>,
)

data class EntryRecordItemResDto(
    @field:Schema(description = "이벤트가 일어난 시각(Asia/Seoul)")
    val occurredAt: LocalDateTime,
    @field:Schema(description = "`ENTER` 최초 입장, `RE_ENTER` 재입장, `EXIT` 퇴장")
    val eventType: EntryEventType,
    @field:Schema(description = "`STANDARD` 일반 참가자, `TRAINEE` 연수자, `PAPER_QR` 종이 QR")
    val subjectType: EntrySubjectType,
    @field:Schema(description = "참가자·연수자 ID. 종이 QR은 토큰이 비밀이라 비어 있다", nullable = true)
    val subjectId: Long?,
    @field:Schema(description = "종이 QR의 구분(`ADULT`·`CHILD` 등). 그 밖에는 비어 있다", nullable = true)
    val category: String?,
    @field:Schema(description = "종이 QR에 인쇄된 연번 코드(`ADT-0001-K7`). 코드가 없는 이전 토큰과 다른 종류는 비어 있다", nullable = true)
    val paperCode: String?,
    @field:Schema(description = "처리한 스캐너(기기) 이름. `X-Scanner-Name`을 보내지 않았으면 비어 있다", nullable = true)
    val scanner: String?,
)

data class EntryRecordWalkInResDto(
    val date: LocalDate,
    @field:Schema(description = "간편입장 구분")
    val category: String,
    @field:Schema(description = "그 날짜·구분의 순증감(추가 - 취소)")
    val count: Long,
)

enum class EntryEventType { ENTER, RE_ENTER, EXIT }
