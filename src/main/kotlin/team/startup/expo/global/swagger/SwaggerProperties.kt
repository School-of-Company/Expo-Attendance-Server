package team.startup.expo.global.swagger

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * API 문서(Swagger UI) 노출 설정. 기본은 둘 다 꺼 둔다.
 *
 * @property enabled 문서를 제공할지. 켜면 보안 설정이 문서 경로의 `GET`을 열어 준다(게이트웨이도 경로를 열어야 밖에서 보인다).
 * @property includeInternal 켜면 `/internal` 하위 엔드포인트(서비스 간 호출용)도 문서에 싣는다.
 */
@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    val enabled: Boolean = false,
    val includeInternal: Boolean = false,
)
