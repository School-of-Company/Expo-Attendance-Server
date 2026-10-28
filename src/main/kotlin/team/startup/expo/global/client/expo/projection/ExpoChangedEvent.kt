package team.startup.expo.global.client.expo.projection

import java.time.Instant

/**
 * `expo.expo.changed` 메시지 중 이 서비스가 쓰는 값(Expo-Expo-Server `docs/expo-change-events.md`). 모르는 필드는 버린다.
 * [deleted]면 tombstone이라 [expo]와 목록이 비어 있다.
 */
data class ExpoChangedEvent(
    val eventId: String,
    val version: Long,
    val expoId: String,
    val deleted: Boolean,
    val expo: ExpoState? = null,
    val standardPrograms: List<StandardProgramState> = emptyList(),
    val trainingPrograms: List<TrainingProgramState> = emptyList(),
    val preregisterSessions: List<PreregisterSessionState> = emptyList(),
) {
    data class ExpoState(
        val startedDay: String,
        val finishedDay: String,
        val deleting: Boolean,
        /** 진행 중인 프로그램 변경이 있다. 박람회 서비스는 그동안 프로그램 조회를 409로 답한다. 필드가 없는 예전 메시지는 `false`. */
        val programChangePending: Boolean = false,
    )

    /** 일정은 KST 지역 일시 `yyyy-MM-ddTHH:mm` 문자열이다. */
    data class StandardProgramState(
        val id: Long,
        val startedAt: String?,
        val endedAt: String?,
    )

    data class TrainingProgramState(
        val id: Long,
    )

    data class PreregisterSessionState(
        val id: Long,
        val startedAt: Instant,
        val endedAt: Instant,
    )

    companion object {
        const val TOPIC = "expo.expo.changed"
        const val SCHEMA_VERSION = 1
    }
}
