package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/** Expo-Application-Server #162와 같은 값이다. 이 서비스는 `ATTENDANCE`만 보낸다. */
enum class AdminDataChangeResource { APPLICATIONS, ATTENDANCE }

/** SSE `admin-data-changed`의 data. 다시 조회할 리소스만 알리며 이름·전화번호·QR 코드·토큰·스캐너 이름을 담지 않는다. */
@Schema(description = "관리자 데이터 변경 신호. 받으면 해당 리소스의 기존 GET을 현재 필터로 다시 조회한다.")
data class AdminDataChangeEvent(
    @field:Schema(description = "이벤트 ID. SSE id와 같다") val eventId: UUID,
    @field:Schema(description = "박람회 ID") val expoId: String,
    @field:Schema(description = "변경된 리소스. 이 서비스는 ATTENDANCE만 보낸다") val resource: AdminDataChangeResource,
    @field:Schema(description = "신호를 만든 시각(UTC)") val occurredAt: Instant,
)
