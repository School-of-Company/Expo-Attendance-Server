package team.startup.expo.global.client.expo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailFeatureDisabled
import team.startup.expo.support.shouldFailWith

class ExpoFeaturePolicySpec :
    BehaviorSpec({
        val expoId = "expo-1"
        val expoClient = mockk<ExpoClient>()
        val policy = ExpoFeaturePolicy(expoClient, UnitTestSupport.circuitBreaker("expo"))
        val unavailable = "박람회 서비스를 잠시 사용할 수 없습니다."

        fun item(
            key: String,
            effective: Any?,
        ) = mapOf("key" to key, "configuredEnabled" to true, "effectiveEnabled" to effective)

        given("박람회 서비스가 기능 설정을 돌려주면") {
            every { expoClient.getFeatures(expoId) } returns
                ExpoFeaturesResDto(listOf(item("QR_ADMISSION", true), item("PAPER_QR", false)))

            then("effectiveEnabled 값을 그대로 쓴다") {
                policy.isEnabled(expoId, ExpoFeature.QR_ADMISSION) shouldBe true
                policy.isEnabled(expoId, ExpoFeature.PAPER_QR) shouldBe false
            }
            then("꺼진 기능을 require하면 409다") {
                shouldFailFeatureDisabled(ExpoFeature.PAPER_QR) { policy.require(expoId, ExpoFeature.PAPER_QR) }
            }
            then("키가 없으면 허용으로 보지 않고 503이다") {
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.isEnabled(expoId, ExpoFeature.STANDARD_ATTENDANCE) }
            }
        }

        given("해석할 수 없는 응답이면") {
            then("features 필드가 없는 구형 응답은 503이다") {
                every { expoClient.getFeatures(expoId) } returns ExpoFeaturesResDto(null)
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
            then("effectiveEnabled가 boolean이 아니면 503이다") {
                every { expoClient.getFeatures(expoId) } returns ExpoFeaturesResDto(listOf(item("QR_ADMISSION", "true")))
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
            then("JSON 해석 실패는 503이다") {
                every { expoClient.getFeatures(expoId) } throws UnitTestSupport.feignDecodeError()
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
        }

        given("박람회 서비스 호출이 실패하면") {
            then("5xx는 503이다") {
                every { expoClient.getFeatures(expoId) } throws UnitTestSupport.feignServerError()
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
            then("타임아웃은 503이다") {
                every { expoClient.getFeatures(expoId) } throws UnitTestSupport.feignReadTimeout()
                shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, unavailable) { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
            then("404는 기존 박람회 없음 404다") {
                every { expoClient.getFeatures(expoId) } throws UnitTestSupport.feignNotFound()
                shouldFailWith(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.") { policy.require(expoId, ExpoFeature.QR_ADMISSION) }
            }
        }
    })
