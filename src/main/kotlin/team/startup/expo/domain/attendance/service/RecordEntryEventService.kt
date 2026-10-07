package team.startup.expo.domain.attendance.service

import java.time.LocalDate

interface RecordEntryEventService {
    /**
     * 일반 참가자의 입장 이벤트를 아웃박스에 남긴다. 같은 날 같은 참가자는 한 번만 남고, 삭제된 박람회에는 남기지 않는다.
     * 남겼으면 `true`, 이미 있거나 삭제된 박람회여서 남기지 않았으면 `false`다.
     */
    fun record(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
        attendanceDate: LocalDate,
    ): Boolean
}
