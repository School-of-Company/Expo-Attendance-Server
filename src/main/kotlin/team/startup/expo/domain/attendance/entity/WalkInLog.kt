package team.startup.expo.domain.attendance.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/** 간편입장 증감 한 번(+1, 취소는 -1)(#118). 합계는 이 행들의 합이다. */
@Entity
@Table(name = "tb_walk_in_log")
class WalkInLog(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:Column(name = "category", nullable = false, length = 20)
    val category: String,
    @field:Column(name = "delta", nullable = false)
    val delta: Short,
    @field:Column(name = "request_id", length = 64)
    val requestId: String? = null,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    /** 요청을 보낸 스캐너(기기) 이름(#131). 보내지 않았으면 비어 있다. */
    @field:Column(name = "scanner", length = 40)
    val scanner: String? = null,
)
