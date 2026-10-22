package team.startup.expo.support

import org.springframework.jdbc.core.JdbcTemplate
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.repository.FieldVisitRepository
import team.startup.expo.global.client.user.RecordEntryReqDto
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

object FieldVisitFixtures {
    fun activate(
        jdbc: JdbcTemplate,
        clock: Clock,
        expoId: String,
        participantId: Long,
    ): UUID {
        val registration = UUID.nameUUIDFromBytes("$expoId:$participantId".toByteArray())
        FieldVisitRepository(jdbc).save(
            FieldVisit(registration, expoId, participantId, LocalDate.now(clock), 1, FieldVisitStatus.ACTIVE, registration),
        )
        return registration
    }

    fun entry(
        jdbc: JdbcTemplate,
        clock: Clock,
        request: RecordEntryReqDto,
        participantId: Long = requireNotNull(request.participantId),
    ): RecordEntryReqDto {
        if (request.participationType != "STANDARD") return request
        val registration = activate(jdbc, clock, request.expoId, participantId)
        return request.copy(attendanceDate = LocalDate.now(clock), fieldVisitRegistrationId = registration, fieldVisitGeneration = 1)
    }

    fun clear(jdbc: JdbcTemplate) {
        jdbc.execute("TRUNCATE TABLE tb_field_visit_guard, tb_field_visit, tb_entry_scan_fence")
    }
}
