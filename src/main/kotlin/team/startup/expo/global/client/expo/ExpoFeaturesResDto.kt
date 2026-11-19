package team.startup.expo.global.client.expo

/**
 * 박람회 서비스 `GET /internal/expo/{expoId}` 응답의 기능 설정 부분. 구형 응답에는 `features`가 없어 `null`이다.
 * 항목은 원문 JSON 값 그대로 받아 boolean이 아닌 값도 "해석 불가"로 구분한다.
 */
data class ExpoFeaturesResDto(
    val features: List<Map<String, Any?>>? = null,
)
