package team.startup.expo.global.security

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.exception.ExpectedException

class ExpoAccessGuardSpec :
    BehaviorSpec({
        val request = mockk<HttpServletRequest> { every { getHeader("Authorization") } returns "Bearer a" }

        given("허용 판정을 받은 토큰") {
            `when`("Expo 회로가 열린 뒤 같은 박람회·권한을 다시 확인하면") {
                then("Expo를 다시 부르지 않고 통과한다") {
                    val client = mockk<ExpoClient> { every { requireAdminAccess("e1", "Bearer a", "CHECKIN") } just runs }
                    val breaker = CircuitBreaker.ofDefaults("t1")
                    val guard = ExpoAccessGuard(client, breaker, request, 30)
                    guard.requireAccess("e1", "CHECKIN")
                    breaker.transitionToOpenState()
                    guard.requireAccess("e1", "CHECKIN")
                    verify(exactly = 1) { client.requireAdminAccess("e1", "Bearer a", "CHECKIN") }
                }
            }
            `when`("다른 권한을 확인하는데 회로가 열려 있으면") {
                then("기억한 판정을 쓰지 않고 503이다") {
                    val client = mockk<ExpoClient> { every { requireAdminAccess(any(), any(), any()) } just runs }
                    val breaker = CircuitBreaker.ofDefaults("t2")
                    val guard = ExpoAccessGuard(client, breaker, request, 30)
                    guard.requireAccess("e1", "CHECKIN")
                    breaker.transitionToOpenState()
                    shouldThrow<ExpectedException> { guard.requireAccess("e1", "STATS_READ") }.status.value() shouldBe 503
                }
            }
        }
    })
