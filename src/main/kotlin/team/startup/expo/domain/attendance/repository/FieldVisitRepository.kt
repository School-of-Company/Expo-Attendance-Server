package team.startup.expo.domain.attendance.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitGuardState
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

data class FieldVisit(
    val registrationId: UUID,
    val expoId: String,
    val participantId: Long,
    val visitDate: LocalDate,
    val generation: Long,
    val status: FieldVisitStatus,
    val operationId: UUID,
)

data class FieldVisitGuard(
    val registrationId: UUID,
    val generation: Long,
    val operationId: UUID,
    val state: FieldVisitGuardState,
)

@Repository
class FieldVisitRepository(
    private val jdbc: JdbcTemplate,
) {
    fun find(registrationId: UUID): FieldVisit? =
        jdbc.query("SELECT * FROM tb_field_visit WHERE registration_id = ?", { rs, _ -> row(rs) }, registrationId).singleOrNull()

    fun visits(
        expoId: String,
        participantId: Long,
    ): List<FieldVisit> =
        jdbc.query(
            "SELECT * FROM tb_field_visit WHERE expo_id = ? AND participant_id = ? ORDER BY registration_id",
            { rs, _ -> row(rs) },
            expoId,
            participantId,
        )

    private fun row(rs: ResultSet) =
        FieldVisit(
            rs.getObject("registration_id", UUID::class.java),
            rs.getString("expo_id"),
            rs.getLong("participant_id"),
            rs.getObject("visit_date", LocalDate::class.java),
            rs.getLong("generation"),
            FieldVisitStatus.valueOf(rs.getString("status")),
            rs.getObject("operation_id", UUID::class.java),
        )

    fun save(visit: FieldVisit) {
        jdbc.update(
            """
            INSERT INTO tb_field_visit (registration_id, expo_id, participant_id, visit_date, generation, status, operation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (registration_id) DO UPDATE SET generation = EXCLUDED.generation,
                status = EXCLUDED.status, operation_id = EXCLUDED.operation_id
            """.trimIndent(),
            visit.registrationId,
            visit.expoId,
            visit.participantId,
            visit.visitDate,
            visit.generation,
            visit.status.name,
            visit.operationId,
        )
    }

    fun guards(
        registrationId: UUID,
        generation: Long,
    ): List<FieldVisitGuard> =
        jdbc.query(
            "SELECT * FROM tb_field_visit_guard WHERE registration_id = ? AND generation = ?",
            { rs, _ ->
                FieldVisitGuard(
                    registrationId,
                    generation,
                    rs.getObject("operation_id", UUID::class.java),
                    FieldVisitGuardState.valueOf(rs.getString("state")),
                )
            },
            registrationId,
            generation,
        )

    fun saveGuard(
        visit: FieldVisit,
        operationId: UUID,
        state: FieldVisitGuardState,
        traineeId: Long?,
    ) {
        jdbc.update(
            """
            INSERT INTO tb_field_visit_guard
                (registration_id, generation, operation_id, expo_id, participant_id, visit_date, trainee_id, state)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (registration_id, generation, operation_id) DO UPDATE SET state = EXCLUDED.state
            """.trimIndent(),
            visit.registrationId,
            visit.generation,
            operationId,
            visit.expoId,
            visit.participantId,
            visit.visitDate,
            traineeId,
            state.name,
        )
    }

    fun held(
        expoId: String,
        participantId: Long?,
        traineeId: Long?,
        date: LocalDate,
    ): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS (SELECT 1 FROM tb_field_visit_guard g JOIN tb_field_visit v
                ON v.registration_id = g.registration_id AND v.generation = g.generation
                WHERE g.expo_id = ? AND g.visit_date = ? AND g.state = 'HELD'
                AND (g.participant_id = ? OR g.trainee_id = ?))
            """.trimIndent(),
            Boolean::class.java,
            expoId,
            date,
            participantId,
            traineeId,
        ) == true

    fun linkedParticipants(
        expoId: String,
        traineeId: Long,
        date: LocalDate,
    ): List<Long> =
        jdbc.query(
            "SELECT DISTINCT participant_id FROM tb_field_visit_guard WHERE expo_id = ? AND trainee_id = ? AND visit_date = ?",
            { rs, _ -> rs.getLong("participant_id") },
            expoId,
            traineeId,
            date,
        )

    fun deleteExpo(expoId: String) {
        jdbc.update("DELETE FROM tb_field_visit_guard WHERE expo_id = ?", expoId)
        jdbc.update("DELETE FROM tb_field_visit WHERE expo_id = ?", expoId)
        jdbc.update("DELETE FROM tb_entry_scan_fence WHERE expo_id = ?", expoId)
    }
}
