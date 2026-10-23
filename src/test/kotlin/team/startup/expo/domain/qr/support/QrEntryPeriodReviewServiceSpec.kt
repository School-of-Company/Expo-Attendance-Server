package team.startup.expo.domain.qr.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.support.ExpoPeriodValidator
import team.startup.expo.domain.qr.entity.QrEntryPeriodCheck
import team.startup.expo.domain.qr.repository.PendingQrEntry
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.lock.AdvisoryJobLock
import java.time.LocalDate

class QrEntryPeriodReviewServiceSpec : BehaviorSpec() {
    // 테스트(말단)마다 새 인스턴스를 만들어 목 호출 기록이 섞이지 않게 한다
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val qrEntryRepository = mockk<QrEntryRepository>(relaxed = true)
    private val periodValidator = mockk<ExpoPeriodValidator>()
    private val jobLock = mockk<AdvisoryJobLock>()
    private val service = QrEntryPeriodReviewService(qrEntryRepository, periodValidator, jobLock)

    private val today = LocalDate.of(2026, 5, 10)

    /** 다른 인스턴스가 쥐고 있는 락 키들. 여기에 든 키는 건너뛰고, 나머지는 콜백을 그대로 실행한다. */
    private fun givenLockedBy(otherInstance: Set<String>) {
        every { jobLock.runIfAcquired<Int>(any(), any()) } answers {
            if (firstArg<String>() in otherInstance) null else secondArg<() -> Int>().invoke()
        }
    }

    private fun givenPending(
        expoId: String,
        vararg entryIds: Long,
    ) {
        every { qrEntryRepository.findByPeriodCheckAndExpoId(QrEntryPeriodCheck.PENDING, expoId, any()) } returnsMany
            listOf(entryIds.map { PendingQrEntry(it, expoId, today) }, emptyList())
    }

    init {
        given("재확인 대기 입장이 있는 박람회에서") {
            every { qrEntryRepository.findExpoIdsByPeriodCheck(QrEntryPeriodCheck.PENDING) } returns listOf("expo-1")
            every { qrEntryRepository.updatePeriodCheck(any(), any()) } answers { firstArg<Collection<Long>>().size }

            `when`("다른 인스턴스가 그 박람회의 락을 쥐고 있으면") {
                givenLockedBy(setOf("qr-entry-review:expo-1"))

                val changed = service.reviewPending()

                then("기간을 확인하지도 상태를 바꾸지도 않고 0을 돌려준다") {
                    changed shouldBe 0
                    verify(exactly = 0) { periodValidator.period(any()) }
                    verify(exactly = 0) { qrEntryRepository.updatePeriodCheck(any(), any()) }
                }
            }

            `when`("락을 잡으면") {
                givenLockedBy(emptySet())
                givenPending("expo-1", 1L, 2L)
                every { periodValidator.period("expo-1") } returns (today.minusDays(1) to today.plusDays(1))

                val changed = service.reviewPending()

                then("박람회별 락 키로 처리하고 기간 안의 입장을 VERIFIED로 바꾼다") {
                    changed shouldBe 2
                    verify(exactly = 1) { jobLock.runIfAcquired<Int>("qr-entry-review:expo-1", any()) }
                    verify { qrEntryRepository.updatePeriodCheck(listOf(1L, 2L), QrEntryPeriodCheck.VERIFIED) }
                }
            }
        }

        given("재확인 대기 입장이 있는 박람회가 둘이고") {
            every { qrEntryRepository.findExpoIdsByPeriodCheck(QrEntryPeriodCheck.PENDING) } returns listOf("expo-1", "expo-2")
            every { qrEntryRepository.updatePeriodCheck(any(), any()) } answers { firstArg<Collection<Long>>().size }
            givenPending("expo-2", 7L)
            every { periodValidator.period("expo-2") } returns (today.minusDays(1) to today.plusDays(1))

            `when`("한 박람회만 다른 인스턴스가 처리 중이면") {
                givenLockedBy(setOf("qr-entry-review:expo-1"))

                val changed = service.reviewPending()

                then("그 박람회만 건너뛰고 다른 박람회는 처리한다") {
                    changed shouldBe 1
                    verify(exactly = 0) { periodValidator.period("expo-1") }
                    verify { qrEntryRepository.updatePeriodCheck(listOf(7L), QrEntryPeriodCheck.VERIFIED) }
                }
            }
        }

        given("락을 잡은 박람회에서") {
            every { qrEntryRepository.findExpoIdsByPeriodCheck(QrEntryPeriodCheck.PENDING) } returns listOf("expo-1")
            every { qrEntryRepository.updatePeriodCheck(any(), any()) } answers { firstArg<Collection<Long>>().size }
            givenLockedBy(emptySet())
            givenPending("expo-1", 1L)

            `when`("박람회 서비스가 응답하지 못하면") {
                every { periodValidator.period("expo-1") } throws ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable")

                val changed = service.reviewPending()

                then("입장을 PENDING으로 남기고 상태를 바꾸지 않는다") {
                    changed shouldBe 0
                    verify(exactly = 0) { qrEntryRepository.updatePeriodCheck(any(), any()) }
                }
            }

            `when`("박람회가 없다고 하면") {
                every { periodValidator.period("expo-1") } throws ExpectedException(HttpStatus.NOT_FOUND, "not found")

                val changed = service.reviewPending()

                then("입장을 지우지 않고 EXPO_NOT_FOUND로 바꾼다") {
                    changed shouldBe 1
                    verify { qrEntryRepository.updatePeriodCheck(listOf(1L), QrEntryPeriodCheck.EXPO_NOT_FOUND) }
                    verify(exactly = 0) { qrEntryRepository.deleteAll() }
                }
            }

            `when`("입장한 날이 박람회 기간 밖이면") {
                every { periodValidator.period("expo-1") } returns (today.plusDays(1) to today.plusDays(3))

                val changed = service.reviewPending()

                then("OUT_OF_PERIOD로 바꾼다") {
                    changed shouldBe 1
                    verify { qrEntryRepository.updatePeriodCheck(listOf(1L), QrEntryPeriodCheck.OUT_OF_PERIOD) }
                }
            }
        }
    }
}
