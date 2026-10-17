package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 실시간 입·퇴장 현황 변경 SSE(#141, `docs/admin-data-change-events.md`). 기본은 꺼져 있다.
 *
 * @property enabled 켜면 구독 경로와 DB LISTEN 연결이 생긴다. Gateway의 `text/event-stream` 전달을 확인한 뒤 켠다.
 * @property maxConnections 인스턴스당 최대 연결 수.
 * @property maxConnectionsPerExpo 박람회 하나의 최대 연결 수(인스턴스당).
 * @property connectionLifetime 연결 최대 수명. 끝나면 서버가 닫고, 재연결 때 게이트웨이가 권한을 다시 검증한다.
 * @property heartbeatInterval SSE 주석 heartbeat 간격.
 * @property coalesceInterval 같은 박람회의 변경을 이 시간 동안 모아 한 번만 보낸다. 연속 스캔이 화면의 연속 재조회가 되지 않게 한다. 0이면 모으지 않는다.
 */
@ConfigurationProperties(prefix = "admin-events")
data class AdminEventsProperties(
    val enabled: Boolean = false,
    val maxConnections: Int = 500,
    val maxConnectionsPerExpo: Int = 50,
    val connectionLifetime: Duration = Duration.ofMinutes(30),
    val heartbeatInterval: Duration = Duration.ofSeconds(25),
    val coalesceInterval: Duration = Duration.ofSeconds(1),
)
