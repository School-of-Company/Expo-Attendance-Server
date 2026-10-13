package team.startup.expo.domain.attendance.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import java.time.LocalDate
import java.time.LocalDateTime

interface EntryLogRepository : JpaRepository<EntryLog, Long> {
    fun findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
        expoId: String,
        subjectType: EntrySubjectType,
        subjectId: String,
        attendanceDate: LocalDate,
    ): EntryLog?

    /** 오늘 입장 로그를 남긴다. 이미 있으면 아무것도 하지 않는다(같은 날 한 번만 입장하므로 하루 한 행). */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tb_entry_log (expo_id, subject_type, subject_id, attendance_date, entered_at, category)
            VALUES (:expoId, :subjectType, :subjectId, :attendanceDate, :enteredAt, :category)
            ON CONFLICT (expo_id, subject_type, subject_id, attendance_date) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("expoId") expoId: String,
        @Param("subjectType") subjectType: String,
        @Param("subjectId") subjectId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("enteredAt") enteredAt: LocalDateTime,
        @Param("category") category: String?,
    ): Int

    /** 오늘 입장했고 아직 퇴장하지 않은 경우에만 퇴장 시각을 기록한다. 0이면 입장 기록이 없거나 이미 퇴장했다. */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tb_entry_log SET exited_at = :exitedAt
            WHERE expo_id = :expoId AND subject_type = :subjectType AND subject_id = :subjectId
              AND attendance_date = :attendanceDate AND exited_at IS NULL
        """,
    )
    fun markExited(
        @Param("expoId") expoId: String,
        @Param("subjectType") subjectType: String,
        @Param("subjectId") subjectId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
        @Param("exitedAt") exitedAt: LocalDateTime,
    ): Int

    /** 종류·구분별 [종류, 구분, 입장 수, 퇴장 수]. 구분은 종이 QR에만 있다. */
    @Query(
        nativeQuery = true,
        value = """
            SELECT subject_type, category, COUNT(*), COUNT(exited_at) FROM tb_entry_log
            WHERE expo_id = :expoId AND attendance_date = :attendanceDate GROUP BY subject_type, category
        """,
    )
    fun summarize(
        @Param("expoId") expoId: String,
        @Param("attendanceDate") attendanceDate: LocalDate,
    ): List<Array<Any?>>

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM tb_entry_log WHERE expo_id = :expoId")
    fun deleteByExpoId(
        @Param("expoId") expoId: String,
    ): Int
}
