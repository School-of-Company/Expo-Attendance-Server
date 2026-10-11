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

    /** 프로그램별 [프로그램 ID, 출석 인원, 가장 이른 입실, 가장 늦은 입실]. 출석이 없는 프로그램은 행이 없다. */
    @Query(
        "SELECT a.standardProgramId, COUNT(a), MIN(a.entryTime), MAX(a.entryTime) FROM StandardProgramAttendance a " +
            "WHERE a.standardProgramId IN :programIds GROUP BY a.standardProgramId",
    )
    fun summarize(
        @Param("programIds") programIds: Collection<Long>,
    ): List<Array<Any>>

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

    /** 프로그램의 출석 기록을 모두 지운다. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_standard_program_attendance WHERE standard_program_id = :programId")
    fun deleteByProgramId(
        @Param("programId") programId: Long,
    ): Int
}
