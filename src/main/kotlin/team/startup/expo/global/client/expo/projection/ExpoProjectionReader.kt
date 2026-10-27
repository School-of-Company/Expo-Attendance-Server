package team.startup.expo.global.client.expo.projection

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.config.ExpoProjectionProperties
import java.util.concurrent.ConcurrentHashMap

/**
 * 박람회 조회를 프로젝션에서 먼저 찾는다(#240). 꺼져 있으면 DB를 보지 않고 항상 `null`이라 호출하는 쪽이 지금처럼 박람회 서비스를 부른다.
 * 켜져 있어도 프로젝션에 없으면(아직 받지 못함, 삭제 중, tombstone) `null`이고, 호출하는 쪽이 기존 동기 호출(서킷 브레이커 포함)로
 * 넘어간다. 그때 박람회·조회 종류마다 한 번만 로그를 남긴다.
 */
@Component
class ExpoProjectionReader(
    private val repository: ExpoProjectionRepository,
    private val properties: ExpoProjectionProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val loggedMisses = ConcurrentHashMap.newKeySet<String>()

    fun period(expoId: String): ExpoPeriodResDto? = read("period", expoId) { repository.period(expoId) }

    fun standardProgram(
        expoId: String,
        programId: Long,
    ): StandardProgramResDto? = read("standard-program", expoId) { repository.standardProgram(expoId, programId) }

    /** 연수 프로그램이 프로젝션에 있으면 [Unit], 없으면 `null`. */
    fun trainingProgram(
        expoId: String,
        programId: Long,
    ): Unit? = read("training-program", expoId) { if (repository.hasTrainingProgram(expoId, programId)) Unit else null }

    fun preregisterSession(
        expoId: String,
        sessionId: Long,
    ): PreregisterSessionResDto? = read("preregister-session", expoId) { repository.preregisterSession(expoId, sessionId) }

    private fun <T : Any> read(
        kind: String,
        expoId: String,
        lookup: () -> T?,
    ): T? {
        if (!properties.enabled) return null
        val found = lookup()
        if (found == null) logMissOnce(kind, expoId)
        return found
    }

    private fun logMissOnce(
        kind: String,
        expoId: String,
    ) {
        if (loggedMisses.size >= MAX_LOGGED_MISSES) loggedMisses.clear()
        if (loggedMisses.add("$kind:$expoId")) {
            log.info("Expo projection miss, falling back to Expo call: kind={}, expoId={}", kind, expoId)
        }
    }

    private companion object {
        const val MAX_LOGGED_MISSES = 10_000
    }
}
