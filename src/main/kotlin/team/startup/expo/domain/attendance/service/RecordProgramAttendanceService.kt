package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.entity.ProgramType
import java.time.LocalDate
import java.time.LocalTime

enum class ProgramAttendanceResult {
    /** 입실을 기록했다. */
    ENTERED,

    /** 퇴실을 기록했다. */
    LEFT,

    /** 이미 퇴실한 뒤의 스캔이라 아무것도 기록하지 않았다. */
    ALREADY_LEFT,

    /** 삭제된 프로그램이라 기록하지 않았다. */
    PROGRAM_DELETED,
}

interface RecordProgramAttendanceService {
    /**
     * 첫 스캔은 입실, 두 번째는 퇴실을 기록한다. 프로그램 삭제와 같은 락을 잡은 뒤 삭제 기록을 확인하고 한
     * 트랜잭션에서 기록하므로, 삭제와 겹쳐도 삭제 뒤에 출석이 되살아나지 않는다.
     */
    fun record(
        type: ProgramType,
        programId: Long,
        personId: Long,
        attendanceDate: LocalDate,
        time: LocalTime,
    ): ProgramAttendanceResult
}
