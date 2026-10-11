package team.startup.expo.global.swagger

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import team.startup.expo.global.exception.ErrorResponse
import team.startup.expo.global.security.InternalTokenAuthenticationFilter

/**
 * API 문서 내용. `swagger.enabled`가 켜져 있을 때만 등록한다.
 * `/internal` 하위 엔드포인트는 `swagger.include-internal`이 꺼져 있으면 문서에서 뺀다.
 */
@Configuration
@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true")
class OpenApiConfig(
    private val properties: SwaggerProperties,
) {
    @Bean
    fun attendanceOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Expo Attendance Server API")
                    .description(DESCRIPTION),
            ).components(Components())

    @Bean
    fun apiErrorOperationCustomizer(): OperationCustomizer = ApiErrorOperationCustomizer()

    @Bean
    fun attendanceOpenApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components().also { openApi.components = it }
            ModelConverters.getInstance().readAll(ErrorResponse::class.java).forEach { (name, schema) ->
                components.addSchemas(name, schema)
            }
            if (properties.includeInternal) {
                documentInternalToken(openApi, components)
            } else {
                excludeInternal(openApi, components)
            }
        }

    private fun documentInternalToken(
        openApi: OpenAPI,
        components: Components,
    ) {
        components.addSecuritySchemes(
            INTERNAL_TOKEN_SCHEME,
            SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .`in`(SecurityScheme.In.HEADER)
                .name(InternalTokenAuthenticationFilter.TOKEN_HEADER)
                .description("서비스 간 호출 인증용 공유 시크릿. `/internal/**`에만 필요하다."),
        )
        openApi.paths
            ?.filterKeys { it.startsWith(INTERNAL_PREFIX) }
            ?.values
            ?.forEach { item -> item.readOperations().forEach { it.addSecurityItem(SecurityRequirement().addList(INTERNAL_TOKEN_SCHEME)) } }
    }

    /** 경로를 빼고 나면 내부 엔드포인트에서만 쓰던 스키마(참가자 코드 응답 등)도 문서에 남지 않게 함께 뺀다. */
    private fun excludeInternal(
        openApi: OpenAPI,
        components: Components,
    ) {
        openApi.paths?.keys?.removeIf { it.startsWith(INTERNAL_PREFIX) }
        val schemas = components.schemas ?: return
        val mapper = Json.mapper()
        val reachable = mutableSetOf<String>()
        val pending = ArrayDeque(referencedSchemas(mapper.writeValueAsString(openApi.paths)))
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (reachable.add(name)) schemas[name]?.let { pending += referencedSchemas(mapper.writeValueAsString(it)) }
        }
        schemas.keys.retainAll(reachable)
    }

    private fun referencedSchemas(json: String): List<String> = SCHEMA_REF.findAll(json).map { it.groupValues[1] }.toList()

    private companion object {
        const val INTERNAL_PREFIX = "/internal/"
        const val INTERNAL_TOKEN_SCHEME = "internalToken"
        val SCHEMA_REF = Regex("#/components/schemas/([^\"]+)\"")

        const val DESCRIPTION = """박람회 MSA의 참여(출석) 서비스 API입니다. 박람회 입장 스캔, 일반·연수 프로그램 출석 스캔, 현장 종이 QR 발급과 입구 스캔을 다룹니다.

**인증**
- 클라이언트 요청은 게이트웨이를 거쳐 옵니다. 게이트웨이가 JWT를 검증한 뒤 `X-User-Id`와(토큰에 역할이 있으면) `X-User-Role` 헤더를 넘기며, 이 서비스는 토큰을 직접 확인하지 않습니다.
- 관리자 전용 엔드포인트(종이 QR 토큰 발급)는 `X-User-Role`이 `ROLE_ADMIN`이어야 합니다. 헤더가 없으면 401, 다른 역할이면 403입니다.
- `/internal/**`은 서비스 간 호출용이며 `X-Internal-Token` 헤더가 필요합니다. 게이트웨이를 거치지 않고, 문서에는 `SWAGGER_INCLUDE_INTERNAL`을 켠 경우에만 나타납니다.

**오류 형식**: 모든 오류는 `{"status": 400, "message": "..."}` 형태의 `ErrorResponse`로 돌려줍니다."""
    }
}
