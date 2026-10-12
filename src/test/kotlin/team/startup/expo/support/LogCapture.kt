package team.startup.expo.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** 로거 하나의 로그 이벤트를 모아 두는 테스트 도우미. 끝나면 [close]로 되돌린다. */
class LogCapture(
    loggerName: String,
    level: Level = Level.DEBUG,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(loggerName) as Logger
    private val previousLevel = logger.level
    private val appender = ListAppender<ILoggingEvent>().also { it.start() }

    init {
        logger.level = level
        logger.addAppender(appender)
    }

    val events: List<ILoggingEvent> get() = appender.list.toList()

    /** 인자를 채운 완성된 메시지들 */
    val messages: List<String> get() = events.map { it.formattedMessage }

    override fun close() {
        logger.detachAppender(appender)
        logger.level = previousLevel
    }
}
