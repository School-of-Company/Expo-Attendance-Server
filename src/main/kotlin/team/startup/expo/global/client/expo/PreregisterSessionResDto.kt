package team.startup.expo.global.client.expo

import java.time.Instant

/** 박람회 서비스 `GET /internal/expo/{expoId}/preregister-sessions/{sessionId}` 응답. 시각은 UTC `Instant`다. */
data class PreregisterSessionResDto(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant,
)
