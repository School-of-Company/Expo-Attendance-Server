package team.startup.expo.domain.attendance.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import team.startup.expo.support.shouldFailWith

class ExpoIdFormatSpec :
    BehaviorSpec({
        given("박람회 ID 형식") {
            `when`("소문자 UUID이면") {
                then("통과한다") {
                    ExpoIdFormat.isValid("7f3e9c1a-2b4d-4e6f-8a9b-0c1d2e3f4a5b") shouldBe true
                    ExpoIdFormat.requireValid("7f3e9c1a-2b4d-4e6f-8a9b-0c1d2e3f4a5b")
                }
            }
            `when`("대문자, 하이픈 없는 값, 다른 문자열이면") {
                then("올바르지 않다") {
                    ExpoIdFormat.isValid("7F3E9C1A-2B4D-4E6F-8A9B-0C1D2E3F4A5B") shouldBe false
                    ExpoIdFormat.isValid("7f3e9c1a2b4d4e6f8a9b0c1d2e3f4a5b") shouldBe false
                    ExpoIdFormat.isValid("expo-a") shouldBe false
                    ExpoIdFormat.isValid("") shouldBe false
                }
                then("requireValid는 400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "박람회 ID가 올바르지 않습니다.") { ExpoIdFormat.requireValid("expo-a") }
                }
            }
        }
    })
