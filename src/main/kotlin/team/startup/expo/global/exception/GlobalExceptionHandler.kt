package team.startup.expo.global.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionTimedOutException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import team.startup.expo.global.logging.logRejection

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ExpectedException::class)
    fun handleExpectedException(
        exception: ExpectedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logRejection(request, exception.status, exception.message)
        return errorResponse(exception.status, exception.message)
    }

    /**
     * DB 커넥션을 제때 얻지 못했거나(풀 고갈, `connection-timeout`), 트랜잭션·쿼리가 시간 안에 끝나지 않았거나, DB에 닿지 못한 경우다(#102).
     * 서버 버그가 아니라 잠시 뒤에 다시 하면 되는 상황이라 500이 아니라 503으로 알린다. 입구 스캐너가 곧바로 다시 찍을 수 있다.
     */
    @ExceptionHandler(
        CannotCreateTransactionException::class,
        DataAccessResourceFailureException::class,
        TransactionTimedOutException::class,
        QueryTimeoutException::class,
    )
    fun handleTemporaryDatabaseFailure(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.warn("DB 일시 장애로 요청을 처리하지 못했습니다: ${exception.javaClass.simpleName}")
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 잠시 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled API exception", exception)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.")
    }

    // Spring MVC 기본 예외(400, 404, 405, 415 등)는 원래 상태 코드를 유지하고 응답 형식만 맞춘다
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val message = if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) "잘못된 요청입니다." else "요청을 처리할 수 없습니다."
        return ResponseEntity
            .status(statusCode)
            .headers(headers)
            .body(ErrorResponse(status = statusCode.value(), message = message))
    }

    private fun errorResponse(
        status: HttpStatus,
        message: String,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(ErrorResponse(status = status.value(), message = message))
}
