package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.entity.EntrySubjectType
import java.time.LocalDateTime

interface EntryLogService {
    /**
     * 입장을 기록한 직후 입장 로그를 남긴다(#120). 입장 자체는 이미 유저 서비스나 `tb_qr_entry`에 기록되었으므로, 로그를 남기지 못해도
     * 입장을 실패로 만들지 않고 경고만 남긴다(퇴장·집계에서만 빠진다). 오늘 로그가 이미 있고 퇴장한 상태면 재입장으로 다시 연다(#174).
     */
    fun recordEntry(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: String,
        category: String? = null,
        scannerName: String? = null,
        enteredAt: LocalDateTime? = null,
    )
}
