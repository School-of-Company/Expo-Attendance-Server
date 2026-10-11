package team.startup.expo.global.swagger

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.method.HandlerMethod

/**
 * [ApiError]로 적은 오류 상태를 `ErrorResponse` 본문과 함께 응답 목록에 더한다. 이미 `@ApiResponse`로 적은 상태는
 * 건드리지 않는다.
 */
class ApiErrorOperationCustomizer : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        val reasons = linkedMapOf<Int, MutableList<String>>()
        handlerMethod.method.getAnnotationsByType(ApiError::class.java).forEach {
            reasons.getOrPut(it.status) { mutableListOf() } += it.description
        }
        if (isInternal(handlerMethod)) {
            reasons.getOrPut(HTTP_UNAUTHORIZED) { mutableListOf() } += "`X-Internal-Token` 헤더가 없거나 값이 다르다."
        }
        reasons.getOrPut(HTTP_INTERNAL_SERVER_ERROR) { mutableListOf() } += "예상하지 못한 서버 오류."

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        reasons.toSortedMap().forEach { (status, descriptions) ->
            if (responses.containsKey(status.toString())) return@forEach
            responses.addApiResponse(status.toString(), errorResponse(descriptions))
        }
        return operation
    }

    private fun isInternal(handlerMethod: HandlerMethod): Boolean =
        handlerMethod.beanType
            .getAnnotation(RequestMapping::class.java)
            ?.value
            ?.any { it.startsWith("/internal") } == true

    private fun errorResponse(descriptions: List<String>): ApiResponse =
        ApiResponse()
            .description(if (descriptions.size == 1) descriptions.single() else descriptions.joinToString(separator = "\n") { "- $it" })
            .content(
                Content().addMediaType(
                    org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$ERROR_SCHEMA")),
                ),
            )

    companion object {
        const val ERROR_SCHEMA = "ErrorResponse"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
    }
}
