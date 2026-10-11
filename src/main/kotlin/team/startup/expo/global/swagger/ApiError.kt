package team.startup.expo.global.swagger

/**
 * 엔드포인트가 돌려줄 수 있는 오류 상태와 그 원인을 문서에 적는다. 응답 본문은 모두 `ErrorResponse`다.
 * 같은 상태를 여러 번 붙이면 원인이 목록으로 합쳐진다. `/internal` 엔드포인트의 401과 모든 엔드포인트의 500은
 * [ApiErrorOperationCustomizer]가 알아서 더한다.
 */
@Repeatable
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiError(
    val status: Int,
    val description: String,
)
