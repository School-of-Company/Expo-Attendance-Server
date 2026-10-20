package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataAccessResourceFailureException
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EntryLogServiceImplSpec :
    BehaviorSpec({
        val clock = Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"), ZoneOffset.UTC)

        Given("오늘 입장 로그가 이미 있을 때") {
            When("다시 입장을 기록하면") {
                val repository = mockk<EntryLogRepository>()
                every { repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) } returns 0
                every { repository.reenter(any(), any(), any(), any(), any(), any()) } returns 1

                EntryLogServiceImpl(repository, clock).recordEntry("expo", EntrySubjectType.STANDARD, "3")

                Then("퇴장한 상태면 다시 열도록 재입장을 시도한다") {
                    verify(exactly = 1) { repository.reenter("expo", "STANDARD", "3", any(), any(), any()) }
                }
            }
        }

        Given("입장 로그 저장이 일시적으로 실패할 때") {
            When("두 번 실패하고 세 번째에 성공하면") {
                val repository = mockk<EntryLogRepository>()
                var calls = 0
                every { repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) } answers {
                    if (++calls < 3) throw DataAccessResourceFailureException("db") else 1
                }

                EntryLogServiceImpl(repository, clock).recordEntry("expo", EntrySubjectType.STANDARD, "1")

                Then("세 번 시도하고 예외를 던지지 않는다") {
                    verify(exactly = 3) { repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
                }
            }

            When("계속 실패하면") {
                val repository = mockk<EntryLogRepository>()
                every {
                    repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())
                } throws DataAccessResourceFailureException("db")

                EntryLogServiceImpl(repository, clock).recordEntry("expo", EntrySubjectType.STANDARD, "2")

                Then("세 번까지만 시도하고 입장은 실패시키지 않는다") {
                    verify(exactly = 3) { repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
                }
            }
        }
    })
