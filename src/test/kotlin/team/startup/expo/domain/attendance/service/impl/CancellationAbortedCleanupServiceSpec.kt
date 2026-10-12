package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.global.config.CancellationAbortedRetentionProperties
import team.startup.expo.global.lock.AdvisoryJobLock
import team.startup.expo.support.UnitTestSupport
import java.time.LocalDateTime

class CancellationAbortedCleanupServiceSpec : BehaviorSpec() {
    // 테스트(말단)마다 새 인스턴스를 만들어 목 호출 기록이 섞이지 않게 한다
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val repository = mockk<CancellationGuardRepository>(relaxed = true)
    private val jobLock = mockk<AdvisoryJobLock>()
    private val clock = UnitTestSupport.fixedClock()

    private fun service(days: Int) =
        CancellationAbortedCleanupService(repository, CancellationAbortedRetentionProperties(days), jobLock, clock)

    private fun givenLock(acquired: Boolean) {
        every { jobLock.runIfAcquired<Int>("cancellation-aborted-cleanup", any()) } answers {
            if (acquired) secondArg<() -> Int>().invoke() else null
        }
    }

    init {
        given("보관일이 0(기본)이면") {
            `when`("정리를 돌려도") {
                val deleted = service(0).cleanup()

                then("락도 잡지 않고 아무것도 지우지 않는다") {
                    deleted shouldBe 0
                    verify(exactly = 0) { jobLock.runIfAcquired<Int>(any(), any()) }
                    verify(exactly = 0) { repository.deleteAbortedCreatedBefore(any()) }
                }
            }
        }

        given("보관일이 30일이면") {
            `when`("락을 잡으면") {
                givenLock(true)
                every { repository.deleteAbortedCreatedBefore(any()) } returns 3

                val deleted = service(30).cleanup()

                then("지금으로부터 30일 전보다 오래된 기록만 지우고 건수를 돌려준다") {
                    deleted shouldBe 3
                    verify(exactly = 1) { repository.deleteAbortedCreatedBefore(LocalDateTime.now(clock).minusDays(30)) }
                }
            }

            `when`("다른 인스턴스가 정리 중이면") {
                givenLock(false)

                val deleted = service(30).cleanup()

                then("건너뛰고 아무것도 지우지 않는다") {
                    deleted shouldBe 0
                    verify(exactly = 0) { repository.deleteAbortedCreatedBefore(any()) }
                }
            }
        }
    }
}
