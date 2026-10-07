package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.attendance.entity.TrainingProgramAttendance

interface TrainingProgramAttendanceRepository : JpaRepository<TrainingProgramAttendance, Long> {
    fun findByTraineeIdAndTrainingProgramId(
        traineeId: Long,
        trainingProgramId: Long,
    ): TrainingProgramAttendance?
}
