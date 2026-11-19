package team.startup.expo.global.exception

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.HttpStatus
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionTimedOutException
import team.startup.expo.global.client.expo.ExpoFeature
import team.startup.expo.global.client.expo.ExpoFeaturePolicy
import tools.jackson.databind.json.JsonMapper

/** DB 없이 도는 단위 테스트: 커넥션 풀 고갈 같은 일시적인 DB 장애는 500이 아니라 503으로 알린다(#102). */
class GlobalExceptionHandlerSpec :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val handler = GlobalExceptionHandler()

        given("DB가 일시적으로 요청을 받지 못할 때") {
            val cases =
                mapOf(
                    "트랜잭션을 시작하지 못함(커넥션 대기 초과)" to CannotCreateTransactionException("pool exhausted"),
                    "DB에 닿지 못함" to DataAccessResourceFailureException("connection refused"),
                    "트랜잭션 시간 초과" to TransactionTimedOutException("timed out"),
                    "쿼리 시간 초과" to QueryTimeoutException("statement timeout"),
                )
            cases.forEach { (name, exception) ->
                `when`(name) {
                    val response = handler.handleTemporaryDatabaseFailure(exception)

                    then("503과 다시 시도하라는 안내를 돌려준다") {
                        response.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                        response.body!!.status shouldBe 503
                        response.body!!.message shouldBe "요청이 몰려 잠시 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    }
                }
            }
        }

        given("ExpectedException 응답 본문은") {
            val mapper = JsonMapper.builder().build()

            then("기능 OFF 409에는 기존 필드에 code=FEATURE_DISABLED를 더한다") {
                val response = handler.handleExpectedException(ExpoFeaturePolicy.disabled(ExpoFeature.QR_ADMISSION), mockk(relaxed = true))
                response.statusCode shouldBe HttpStatus.CONFLICT
                mapper.writeValueAsString(response.body) shouldBe
                    """{"status":409,"message":"이 박람회에서 사용하지 않는 기능입니다: QR_ADMISSION","code":"FEATURE_DISABLED"}"""
            }
            then("다른 오류는 code 없이 기존 모양 그대로다") {
                val response =
                    handler.handleExpectedException(
                        ExpectedException(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다."),
                        mockk(relaxed = true),
                    )
                mapper.writeValueAsString(response.body) shouldBe """{"status":409,"message":"신청 취소를 처리 중입니다."}"""
            }
        }

        given("예상하지 못한 예외가 나면") {
            val response = handler.handleUnexpected(IllegalStateException("bug"))

            then("그대로 500이다") {
                response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
    })
