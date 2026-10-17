package team.startup.expo.domain.attendance.service.impl

import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * V18 트리거가 커밋 시점에 보내는 `attendance_admin_changes` NOTIFY를 인스턴스마다 하나의 연결로 받아 구독자에게 전달한다(#141).
 * 연결이 끊기면 다시 LISTEN 하고, 그 사이 신호를 잃었을 수 있으므로 모든 구독자에게 재조회 신호를 보낸다.
 *
 * 종료할 때는 웹 서버의 graceful shutdown보다 먼저 멈추며(기본 phase가 더 높다) 열린 SSE 연결을 모두 닫는다. 닫지 않으면 진행 중인
 * 요청으로 남아 `SHUTDOWN_TIMEOUT`까지 종료를 붙잡는다. 클라이언트는 다른 인스턴스로 재연결해 GET으로 다시 읽는다.
 */
@Component
@ConditionalOnProperty("admin-events.enabled", havingValue = "true")
class AdminDataChangeListener(
    private val dataSource: DataSource,
    private val stream: AdminDataChangeStream,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile private var running = false

    @Volatile private var listening = false

    fun isListening() = listening

    private var thread: Thread? = null

    override fun start() {
        running = true
        thread = Thread(::run, "attendance-admin-data-listener").apply { isDaemon = true }.also(Thread::start)
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread?.join(5_000)
        listening = false
        stream.destroy()
    }

    override fun isRunning() = running

    private fun run() {
        var backoff = INITIAL_BACKOFF_MS
        var interrupted = false
        while (running) {
            try {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { it.execute("LISTEN $CHANNEL") }
                    val notifications = connection.unwrap(PGConnection::class.java)
                    listening = true
                    if (interrupted) stream.resyncAll()
                    backoff = INITIAL_BACKOFF_MS
                    while (running) {
                        notifications
                            .getNotifications(POLL_MS)
                            .orEmpty()
                            .map { it.parameter }
                            .filter { EXPO_ID_PATTERN.matches(it) }
                            .distinct()
                            .forEach(stream::changed)
                    }
                }
            } catch (exception: Exception) {
                listening = false
                if (!running) break
                interrupted = true
                log.warn("입·퇴장 변경 신호 연결이 끊겼습니다. {}ms 뒤에 다시 연결합니다: {}", backoff, exception.javaClass.simpleName)
                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                    break
                }
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        listening = false
    }

    private companion object {
        const val CHANNEL = "attendance_admin_changes"
        const val POLL_MS = 1_000
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        val EXPO_ID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
