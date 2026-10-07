package team.startup.expo.domain.attendance.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.repository.DeletedProgramRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.attendance.service.DeleteProgramAttendancesService
import java.time.Clock
import java.time.LocalDateTime

/**
 * 프로그램이 삭제되면 그 프로그램의 출석 기록을 지운다(v1은 프로그램 삭제 서비스가 같은 DB에서 함께 지웠다).
 * 출석 기록과 같은 락을 잡아 삭제와 겹친 스캔이 삭제 뒤에 출석을 되살리지 못하게 한다.
 */
@Service
class DeleteProgramAttendancesServiceImpl(
    private val deletedProgramRepository: DeletedProgramRepository,
    private val standardRepository: StandardProgramAttendanceRepository,
    private val trainingRepository: TrainingProgramAttendanceRepository,
    private val clock: Clock,
) : DeleteProgramAttendancesService {
    @Transactional
    override fun delete(
        type: ProgramType,
        programId: Long,
    ) {
        val programKey = type.keyOf(programId)
        deletedProgramRepository.lockProgram(programKey)
        deletedProgramRepository.insertIfAbsent(programKey, LocalDateTime.now(clock))
        when (type) {
            ProgramType.STANDARD -> standardRepository.deleteByProgramId(programId)
            ProgramType.TRAINING -> trainingRepository.deleteByProgramId(programId)
        }
    }
}
