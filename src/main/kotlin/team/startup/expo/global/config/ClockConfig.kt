package team.startup.expo.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/** 출석 날짜와 시각은 JVM 기본 시간대가 아니라 한국 시간 기준이다. */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
