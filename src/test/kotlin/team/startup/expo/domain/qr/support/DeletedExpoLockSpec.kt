package team.startup.expo.domain.qr.support

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.springframework.http.HttpStatus
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.support.shouldFailWith

class DeletedExpoLockSpec :
    BehaviorSpec({
        val expoId = "7f3e9c1a-2b4d-4e6f-8a9b-0c1d2e3f4a5b"

        given("삭제된 박람회 락") {
            `when`("삭제 기록이 없으면") {
                val repository = mockk<DeletedExpoRepository>()
                every { repository.lockExpo(expoId) } returns 1
                every { repository.existsById(expoId) } returns false

                repository.lockAndRequireAlive(expoId)

                then("락을 먼저 잡은 뒤 삭제 기록을 확인하고 통과한다") {
                    verifyOrder {
                        repository.lockExpo(expoId)
                        repository.existsById(expoId)
                    }
                }
            }
            `when`("삭제 기록이 있으면") {
                val repository = mockk<DeletedExpoRepository>()
                every { repository.lockExpo(expoId) } returns 1
                every { repository.existsById(expoId) } returns true

                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.") { repository.lockAndRequireAlive(expoId) }
                }
            }
        }
    })
