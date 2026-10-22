package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.entity.ProgramType
import java.time.LocalDate
import java.time.LocalTime

enum class ProgramAttendanceResult {
    /** 입실을 기록했다. */
    ENTERED,

    /** 퇴실을 기록했다. */
    LEFT,

    /** 같은 분 안의 연속 스캔이거나 이미 퇴실한 뒤라 아무것도 바꾸지 않았다. */
    ALREADY_ENTERED,

    /** 삭제된 프로그램이라 기록하지 않았다. */
    PROGRAM_DELETED,

    /** 입실이 허용되는 시간대가 아니고 이 사람의 입실 기록도 없어 아무것도 기록하지 않았다. */
    ENTRY_NOT_ALLOWED,
}

interface RecordProgramAttendanceService {
    /**
     * 첫 스캔은 입실, 그 뒤 첫 스캔은 퇴실을 기록하고 나머지는 아무것도 바꾸지 않는다. 프로그램 삭제와 같은 락을 잡은 뒤 삭제 기록을 확인하고 한
     * 트랜잭션에서 기록하므로, 삭제와 겹쳐도 삭제 뒤에 출석이 되살아나지 않는다.
     *
     * [entryAllowed]가 `false`면 입실은 만들지 않는다. 이미 입실한 사람의 퇴실은 그대로 받는다(#198).
     */
    fun record(
        type: ProgramType,
        programId: Long,
        personId: Long,
        attendanceDate: LocalDate,
        time: LocalTime,
        entryAllowed: Boolean = true,
    ): ProgramAttendanceResult
}
