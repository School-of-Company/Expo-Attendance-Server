package team.startup.expo.global.swagger

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
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
                // 게이트웨이가 프록시할 때 Host를 인스턴스 주소로 바꾸므로 서버 주소를 요청 Host로 만들지 않고 게이트웨이 기준 상대 경로로 고정한다.
                // 문서 경로가 이미 `/{접두사}/...`라 통합 문서 화면(Try it out)이 게이트웨이 주소로 호출한다.
            ).servers(listOf(Server().url("/")))
            .components(Components())

    @Bean
    fun apiErrorOperationCustomizer(): OperationCustomizer = ApiErrorOperationCustomizer()

    @Bean
    fun attendanceOpenApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components().also { openApi.components = it }
            ModelConverters.getInstance().readAll(ErrorResponse::class.java).forEach { (name, schema) ->
                components.addSchemas(name, schema)
            }
            documentGatewayJwt(openApi, components)
            if (properties.includeInternal) {
                documentInternalToken(openApi, components)
            } else {
                excludeInternal(openApi, components)
            }
        }

    /**
     * 게이트웨이가 JWT를 요구하는 엔드포인트(`/internal` 하위가 아닌 모든 엔드포인트, 이 서비스는 게이트웨이 `publicPaths`에 공개 경로가 없다)에
     * `bearerAuth`를 선언한다. 이 서비스는 토큰을 직접 검증하지 않고 게이트웨이가 검증한 뒤 `X-User-Id`/`X-User-Role`을 넘긴다. 문서 선언만 한다.
     */
    private fun documentGatewayJwt(
        openApi: OpenAPI,
        components: Components,
    ) {
        components.addSecuritySchemes(
            BEARER_SCHEME,
            SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("게이트웨이가 검증하는 액세스 토큰. 이 서비스는 토큰을 직접 확인하지 않는다."),
        )
        openApi.paths
            ?.filterKeys { !it.startsWith(INTERNAL_PREFIX) }
            ?.values
            ?.forEach { item -> item.readOperations().forEach { it.addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME)) } }
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
        const val BEARER_SCHEME = "bearerAuth"
        val SCHEMA_REF = Regex("#/components/schemas/([^\"]+)\"")

        const val DESCRIPTION = """박람회 MSA의 참여(출석) 서비스 API입니다. 박람회 입장 스캔, 일반·연수 프로그램 출석 스캔, 현장 종이 QR 발급과 입구 스캔을 다룹니다.

**인증**
- 클라이언트 요청은 게이트웨이를 거쳐 옵니다. 게이트웨이가 JWT를 검증한 뒤 `X-User-Id`와(토큰에 역할이 있으면) `X-User-Role` 헤더를 넘기며, 이 서비스는 토큰을 직접 확인하지 않습니다.
- 관리자 전용 엔드포인트(종이 QR 토큰 발급·조회·무효화, 간편입장 증감·합계 조회, 입장 처리 건수 집계)는 `X-User-Role`이 `ROLE_ADMIN`이어야 합니다. 헤더가 없으면 401, 다른 역할이면 403입니다.
- `/internal/**`은 서비스 간 호출용이며 `X-Internal-Token` 헤더가 필요합니다. 게이트웨이를 거치지 않고, 문서에는 `SWAGGER_INCLUDE_INTERNAL`을 켠 경우에만 나타납니다.

**오류 형식**: 모든 오류는 `{"status": 400, "message": "..."}` 형태의 `ErrorResponse`로 돌려줍니다."""
    }
}
