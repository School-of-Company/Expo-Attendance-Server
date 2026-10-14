package team.startup.expo.domain.attendance.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.attendance.entity.WalkInCategory
import team.startup.expo.domain.attendance.presentation.dto.request.RecordWalkInReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.WalkInCountResDto
import team.startup.expo.domain.attendance.presentation.dto.response.WalkInCountsResDto
import team.startup.expo.domain.attendance.repository.WalkInLogRepository
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ScannerName
import team.startup.expo.domain.attendance.service.WalkInService
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.exception.ExpectedException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class WalkInServiceImpl(
    transactionManager: PlatformTransactionManager,
    private val walkInLogRepository: WalkInLogRepository,
    private val deletedExpoRepository: DeletedExpoRepository,
    private val expoPeriodValidator: ExpoPeriodValidator,
    private val clock: Clock,
) : WalkInService {
    private val transaction = TransactionTemplate(transactionManager)

    override fun record(
        expoId: String,
        reqDto: RecordWalkInReqDto,
        scannerName: String?,
    ): WalkInCountsResDto {
        checkExpoId(expoId)
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        checkPeriod(expoId, today)

        val delta = reqDto.action.delta
        val category = reqDto.category.name
        val requestId = reqDto.requestId?.takeIf { it.isNotBlank() }
        val scanner = ScannerName.of(scannerName)
        // 박람회 서비스 호출은 트랜잭션 밖에서 끝냈다. 여기서는 락과 기록만 한 트랜잭션으로 한다.
        transaction.executeWithoutResult {
            // 삭제와 같은 락을 잡은 뒤 삭제 기록을 확인해, 삭제된 박람회의 집계가 되살아나지 않게 한다
            deletedExpoRepository.lockExpo(expoId)
            if (deletedExpoRepository.existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")

            walkInLogRepository.lock("walk-in:$expoId:$today:$category")
            // 이미 처리한 requestId의 재전송은 줄일 수 있는지 따지기 전에 현재 합계로 답한다(응답이 유실된 마지막 UNDO의 재시도가 400이 되지 않게)
            if (requestId != null && walkInLogRepository.existsRequest(expoId, requestId)) return@executeWithoutResult
            if (delta < 0 && walkInLogRepository.count(expoId, today, category) < 1) {
                throw ExpectedException(HttpStatus.BAD_REQUEST, "더 줄일 수 없습니다. 오늘 이 구분의 집계가 없습니다.")
            }
            // 같은 requestId의 재전송이면 0건이라 한 번만 세고, 응답은 현재 합계를 그대로 돌려준다
            walkInLogRepository.insert(expoId, today, category, delta, requestId, now, scanner)
        }
        return counts(expoId, today, today, today)
    }

    override fun get(
        expoId: String,
        date: LocalDate?,
    ): WalkInCountsResDto {
        checkExpoId(expoId)
        val day = date ?: LocalDate.now(clock)
        return counts(expoId, day, day, day)
    }

    override fun getBetween(
        expoId: String,
        from: LocalDate,
        to: LocalDate,
    ): WalkInCountsResDto {
        checkExpoId(expoId)
        return counts(expoId, from, to, null)
    }

    private fun counts(
        expoId: String,
        from: LocalDate,
        to: LocalDate,
        date: LocalDate?,
    ): WalkInCountsResDto {
        val sums = walkInLogRepository.countsByCategory(expoId, from, to).associate { (it[0] as String) to (it[1] as Number).toLong() }
        val counts = WalkInCategory.entries.map { WalkInCountResDto(it, sums[it.name] ?: 0L) }
        return WalkInCountsResDto(expoId, date, counts, counts.sumOf { it.count })
    }

    private fun checkExpoId(expoId: String) {
        if (!EXPO_ID_PATTERN.matches(expoId)) throw ExpectedException(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.")
    }

    /** 박람회 서비스가 응답하지 못한 경우(503)에만 기간 확인을 건너뛰고, 박람회가 없거나(404) 진행 중이 아니면(400) 그대로 거부한다. */
    private fun checkPeriod(
        expoId: String,
        today: LocalDate,
    ) {
        try {
            expoPeriodValidator.checkInProgress(expoId, today)
        } catch (e: ExpectedException) {
            if (e.status != HttpStatus.SERVICE_UNAVAILABLE) throw e
        }
    }

    private companion object {
        val EXPO_ID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
