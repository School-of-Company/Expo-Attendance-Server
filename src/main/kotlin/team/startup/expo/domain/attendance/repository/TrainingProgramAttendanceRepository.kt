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

    fun existsByTraineeId(traineeId: Long): Boolean

    /** 프로그램별 [프로그램 ID, 출석 인원, 가장 이른 입실, 가장 늦은 입실]. 출석이 없는 프로그램은 행이 없다. */
    @Query(
        "SELECT a.trainingProgramId, COUNT(a), MIN(a.entryTime), MAX(a.entryTime) FROM TrainingProgramAttendance a " +
            "WHERE a.trainingProgramId IN :programIds GROUP BY a.trainingProgramId",
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

    /**
     * 입실했고 아직 퇴실하지 않았으면 퇴실을 기록한다. 입실과 같은 분 안의 스캔(연속 스캔)은 퇴실로 치지 않는다.
     * 반환값 0이면 기록하지 않았다.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_training_program_attendance SET leave_time = :leaveTime
            WHERE trainee_id = :traineeId AND training_program_id = :programId AND leave_time IS NULL AND entry_time < :leaveTime
        """,
    )
    fun markLeaveIfPresent(
        @Param("programId") programId: Long,
        @Param("traineeId") traineeId: Long,
        @Param("leaveTime") leaveTime: LocalTime,
    ): Int

    /** 프로그램의 출석 기록을 모두 지운다. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_training_program_attendance WHERE training_program_id = :programId")
    fun deleteByProgramId(
        @Param("programId") programId: Long,
    ): Int
}
