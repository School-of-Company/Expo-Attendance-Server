package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.entity.ProgramType

interface DeleteProgramAttendancesService {
    /** 프로그램의 출석 기록을 지우고 삭제 기록을 남긴다. 여러 번 불러도 결과는 같다. */
    fun delete(
        type: ProgramType,
        programId: Long,
    )
}
