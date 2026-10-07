package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.TrainingProgramAttendance
import java.time.LocalDate
import java.time.LocalTime

interface TrainingProgramAttendanceRepository : JpaRepository<TrainingProgramAttendance, Long> {
    fun findByTraineeIdAndTrainingProgramId(
        traineeId: Long,
        trainingProgramId: Long,
    ): TrainingProgramAttendance?

    fun findAllByTrainingProgramId(trainingProgramId: Long): List<TrainingProgramAttendance>

    /** 처음 스캔이면 입실을 기록한다. 반환값 0이면 이미 입실한 사람이다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_training_program_attendance (training_program_id, trainee_id, attendance_date, entry_time)
            VALUES (:programId, :traineeId, :attendanceDate, :entryTime)
            ON CONFLICT (trainee_id, training_program_id) DO NOTHING
        """,
    )
    fun insertEntryIfAbsent(
        @Param("programId") programId: Long,
        @Param("traineeId") traineeId: Long,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("entryTime") entryTime: LocalTime,
    ): Int

    /** 입실했고 아직 퇴실하지 않았으면 퇴실을 기록한다. 반환값 0이면 이미 퇴실했다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_training_program_attendance SET leave_time = :leaveTime
            WHERE trainee_id = :traineeId AND training_program_id = :programId AND leave_time IS NULL
        """,
    )
    fun markLeaveIfPresent(
        @Param("programId") programId: Long,
        @Param("traineeId") traineeId: Long,
        @Param("leaveTime") leaveTime: LocalTime,
    ): Int
}
