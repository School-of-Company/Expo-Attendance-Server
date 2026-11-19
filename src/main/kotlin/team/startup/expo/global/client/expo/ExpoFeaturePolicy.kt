package team.startup.expo.global.client.expo

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.callService
import team.startup.expo.global.exception.ExpectedException

/** 이 서비스가 검사하는 박람회 기능 키. 키 이름은 Expo `ExpoFeatureCatalog`와 같다. */
enum class ExpoFeature {
    QR_ADMISSION,
    PAPER_QR,
    STANDARD_ATTENDANCE,
    TRAINING_ATTENDANCE,
}

/**
 * 박람회 서비스의 기능 설정(`effectiveEnabled`)으로 신규 쓰기를 허용할지 정한다. 꺼진 기능을 놓치지 않도록 프로젝션·캐시가 아니라
 * 매번 박람회 서비스를 부른다. DB 잠금·트랜잭션 밖에서 불러야 한다.
 * 없는 박람회는 404, 박람회 서비스 장애나 `features`가 없거나 읽을 수 없는 응답은 503이다(기본값으로 허용하지 않는다).
 */
@Component
class ExpoFeaturePolicy(
    private val expoClient: ExpoClient,
    @Qualifier("expoFeatureCircuitBreaker") private val expoFeatureCircuitBreaker: CircuitBreaker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun isEnabled(
        expoId: String,
        feature: ExpoFeature,
    ): Boolean {
        val response =
            expoFeatureCircuitBreaker.callService("박람회", ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")) {
                expoClient.getFeatures(expoId)
            }
        // 빈 본문(null)도 해석할 수 없는 응답이다
        val enabled = response?.features?.singleOrNull { it["key"] == feature.name }?.get("effectiveEnabled") as? Boolean
        if (enabled == null) {
            // 호출은 성공이라 DownstreamLog에 남지 않으므로 원인을 여기서 남긴다(본문은 남기지 않는다)
            logger.warn("박람회 기능 설정을 해석하지 못했습니다(features 없음·키 없음·boolean 아님): expoId={}, feature={}", expoId, feature)
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 서비스를 잠시 사용할 수 없습니다.")
        }
        return enabled
    }

    /** 기능이 꺼져 있으면 409다. */
    fun require(
        expoId: String,
        feature: ExpoFeature,
    ) {
        if (!isEnabled(expoId, feature)) throw disabled(feature)
    }

    companion object {
        const val FEATURE_DISABLED = "FEATURE_DISABLED"

        fun disabled(feature: ExpoFeature) =
            ExpectedException(HttpStatus.CONFLICT, "이 박람회에서 사용하지 않는 기능입니다: ${feature.name}", FEATURE_DISABLED)
    }
}
