package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

/** 하루 한 번의 입장과 그 퇴장(#120). 입장 기록의 사본이며 퇴장과 집계를 위해 둔다. */
@Entity
@Table(
    name = "tb_entry_log",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_entry_log_subject_day",
            columnNames = ["expo_id", "subject_type", "subject_id", "attendance_date"],
        ),
    ],
)
class EntryLog(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "subject_type", nullable = false, length = 20)
    val subjectType: EntrySubjectType,
    @field:Column(name = "subject_id", nullable = false, length = 64)
    val subjectId: String,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "entered_at", nullable = false)
    val enteredAt: LocalDateTime,
    @field:Column(name = "exited_at")
    val exitedAt: LocalDateTime? = null,
    /** 종이 QR 토큰의 구분. 다른 입장에는 없다. */
    @field:Column(name = "category", length = 30)
    val category: String? = null,
    /** 입장을 처리한 스캐너(기기) 이름(#131). 보내지 않았으면 비어 있다. */
    @field:Column(name = "scanner", length = 40)
    val scanner: String? = null,
    /** 퇴장을 처리한 스캐너(기기) 이름. */
    @field:Column(name = "exit_scanner", length = 40)
    val exitScanner: String? = null,
)
