package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.StandardProgramAttendance
import java.time.LocalDate
import java.time.LocalTime

interface StandardProgramAttendanceRepository : JpaRepository<StandardProgramAttendance, Long> {
    fun findByParticipantIdAndStandardProgramId(
        participantId: Long,
        standardProgramId: Long,
    ): StandardProgramAttendance?

    fun findAllByStandardProgramId(standardProgramId: Long): List<StandardProgramAttendance>

    /** 처음 스캔이면 입실을 기록한다. 반환값 0이면 이미 입실한 사람이다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_standard_program_attendance (standard_program_id, participant_id, attendance_date, entry_time)
            VALUES (:programId, :participantId, :attendanceDate, :entryTime)
            ON CONFLICT (participant_id, standard_program_id) DO NOTHING
        """,
    )
    fun insertEntryIfAbsent(
        @Param("programId") programId: Long,
        @Param("participantId") participantId: Long,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("entryTime") entryTime: LocalTime,
    ): Int

    /** 입실했고 아직 퇴실하지 않았으면 퇴실을 기록한다. 반환값 0이면 이미 퇴실했다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_standard_program_attendance SET leave_time = :leaveTime
            WHERE participant_id = :participantId AND standard_program_id = :programId AND leave_time IS NULL
        """,
    )
    fun markLeaveIfPresent(
        @Param("programId") programId: Long,
        @Param("participantId") participantId: Long,
        @Param("leaveTime") leaveTime: LocalTime,
    ): Int
}
