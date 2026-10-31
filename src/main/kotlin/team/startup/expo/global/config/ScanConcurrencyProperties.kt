package team.startup.expo.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 동시 스캔 수 제한(#247). 허용 수는 DB 커넥션 풀 크기에서 [reserve]를 뺀 값이다.
 *
 * @property reserve 스캔이 쓰지 못하게 남겨 두는 커넥션 수. 스캔은 게이트 트랜잭션이 커넥션 하나를 쥔 채 fence 쓰기로 하나를 더 요구하므로,
 * 이만큼 남아 있어야 중첩 요청이 반드시 진행된다. 취소 보호 acquire, SSE LISTEN, 스케줄러 몫도 포함한다.
 * @property waitMs 허용 수가 다 찼을 때 자리를 기다리는 최대 시간(밀리초). 넘으면 커넥션을 잡지 않고 바로 503으로 거절한다.
 */
@ConfigurationProperties(prefix = "scan-concurrency")
data class ScanConcurrencyProperties(
    val reserve: Int = 6,
    val waitMs: Long = 500,
)
