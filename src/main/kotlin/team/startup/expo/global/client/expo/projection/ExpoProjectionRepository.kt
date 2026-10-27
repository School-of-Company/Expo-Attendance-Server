package team.startup.expo.global.client.expo.projection

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 박람회 프로젝션 테이블(`V25`). 쓰기는 [ExpoProjectionUpdater]만 한다. */
@Repository
class ExpoProjectionRepository(
    private val jdbc: JdbcTemplate,
) {
    /**
     * 저장된 version보다 큰 메시지일 때만 머리 행을 덮어쓴다(같거나 작으면 아무것도 바꾸지 않는다). 반영했으면 `true`.
     * 행 락을 잡으므로 같은 트랜잭션 안의 자식 행 교체가 끝날 때까지 다른 반영은 기다린다.
     */
    fun upsertHead(event: ExpoChangedEvent): Boolean =
        jdbc.update(
            """
            INSERT INTO tb_expo_projection (expo_id, version, event_id, deleted, deleting, started_day, finished_day, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (expo_id) DO UPDATE SET version = EXCLUDED.version, event_id = EXCLUDED.event_id,
                deleted = EXCLUDED.deleted, deleting = EXCLUDED.deleting, started_day = EXCLUDED.started_day,
                finished_day = EXCLUDED.finished_day, updated_at = now()
            WHERE tb_expo_projection.version < EXCLUDED.version
            """.trimIndent(),
            event.expoId,
            event.version,
            event.eventId,
            event.deleted,
            event.expo?.deleting ?: false,
            event.expo?.startedDay,
            event.expo?.finishedDay,
        ) == 1

    /** 박람회의 프로그램·회차를 [event]의 목록으로 바꾼다. tombstone이면 모두 지운다. */
    fun replaceChildren(event: ExpoChangedEvent) {
        val expoId = event.expoId
        jdbc.update("DELETE FROM tb_expo_projection_standard_program WHERE expo_id = ?", expoId)
        jdbc.update("DELETE FROM tb_expo_projection_training_program WHERE expo_id = ?", expoId)
        jdbc.update("DELETE FROM tb_expo_projection_session WHERE expo_id = ?", expoId)
        if (event.deleted) return

        jdbc.batchUpdate(
            "INSERT INTO tb_expo_projection_standard_program (expo_id, program_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
            event.standardPrograms.distinctBy { it.id }.map { arrayOf<Any?>(expoId, it.id, it.startedAt, it.endedAt) },
        )
        jdbc.batchUpdate(
            "INSERT INTO tb_expo_projection_training_program (expo_id, program_id) VALUES (?, ?)",
            event.trainingPrograms.distinctBy { it.id }.map { arrayOf<Any?>(expoId, it.id) },
        )
        jdbc.batchUpdate(
            "INSERT INTO tb_expo_projection_session (expo_id, session_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
            event.preregisterSessions.distinctBy { it.id }.map {
                arrayOf<Any?>(expoId, it.id, it.startedAt.atOffset(ZoneOffset.UTC), it.endedAt.atOffset(ZoneOffset.UTC))
            },
        )
    }

    /** 마지막으로 반영한 version. 받은 적 없으면 `null`. */
    fun version(expoId: String): Long? =
        jdbc.query("SELECT version FROM tb_expo_projection WHERE expo_id = ?", { rs, _ -> rs.getLong(1) }, expoId).singleOrNull()

    /** 박람회 기간. 동기 조회처럼 삭제 중이어도 돌려준다. tombstone이거나 없으면 `null`. */
    fun period(expoId: String): ExpoPeriodResDto? =
        jdbc
            .query(
                "SELECT started_day, finished_day FROM tb_expo_projection WHERE expo_id = ? AND NOT deleted",
                { rs, _ -> ExpoPeriodResDto(rs.getString("started_day"), rs.getString("finished_day")) },
                expoId,
            ).singleOrNull()

    /** 일반 프로그램. 삭제 중인 박람회(동기 조회는 409)는 `null`이다. */
    fun standardProgram(
        expoId: String,
        programId: Long,
    ): StandardProgramResDto? =
        jdbc
            .query(
                """
                SELECT p.program_id, p.started_at, p.ended_at FROM tb_expo_projection_standard_program p
                JOIN tb_expo_projection e ON e.expo_id = p.expo_id
                WHERE p.expo_id = ? AND p.program_id = ? AND NOT e.deleted AND NOT e.deleting
                """.trimIndent(),
                {
                    rs,
                    _,
                    ->
                    StandardProgramResDto(
                        id = rs.getLong("program_id"),
                        startedAt = rs.getString("started_at"),
                        endedAt = rs.getString("ended_at"),
                    )
                },
                expoId,
                programId,
            ).singleOrNull()

    /** 연수 프로그램이 있는지. 삭제 중인 박람회(동기 조회는 409)는 `false`다. */
    fun hasTrainingProgram(
        expoId: String,
        programId: Long,
    ): Boolean =
        jdbc
            .query(
                """
                SELECT 1 FROM tb_expo_projection_training_program p
                JOIN tb_expo_projection e ON e.expo_id = p.expo_id
                WHERE p.expo_id = ? AND p.program_id = ? AND NOT e.deleted AND NOT e.deleting
                """.trimIndent(),
                { rs, _ -> rs.getInt(1) },
                expoId,
                programId,
            ).isNotEmpty()

    /** 사전등록 회차 시간. 삭제 중인 박람회(동기 조회는 409)는 `null`이다. */
    fun preregisterSession(
        expoId: String,
        sessionId: Long,
    ): PreregisterSessionResDto? =
        jdbc
            .query(
                """
                SELECT s.session_id, s.started_at, s.ended_at FROM tb_expo_projection_session s
                JOIN tb_expo_projection e ON e.expo_id = s.expo_id
                WHERE s.expo_id = ? AND s.session_id = ? AND NOT e.deleted AND NOT e.deleting
                """.trimIndent(),
                { rs, _ ->
                    PreregisterSessionResDto(
                        rs.getLong("session_id"),
                        rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
                        rs.getObject("ended_at", OffsetDateTime::class.java).toInstant(),
                    )
                },
                expoId,
                sessionId,
            ).singleOrNull()
}
