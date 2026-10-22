package team.startup.expo.domain.attendance

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import team.startup.expo.domain.attendance.presentation.InternalFieldVisitController
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitCancellationResDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitGuardState
import team.startup.expo.domain.attendance.service.FieldVisitService
import team.startup.expo.global.exception.GlobalExceptionHandler
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID

class FieldVisitApiSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val service = mockk<FieldVisitService>(relaxed = true)
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(InternalFieldVisitController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    private val registration = UUID.randomUUID()
    private val operation = UUID.randomUUID()
    private val prefix = "/internal/expos/expo/participants/7/field-visits/$registration"
    private val date = LocalDate.of(2026, 10, 22)

    init {
        given("FIELD 내부 HTTP 계약") {
            `when`("operationId를 포함한 projection이면") {
                then("204 empty body이고 DTO에 UUID 날짜 세대가 바인딩된다") {
                    val response =
                        mvc
                            .perform(
                                put(prefix)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                        """{"visitDate":"2026-10-22","generation":2,"status":"ACTIVE","operationId":"$operation"}""",
                                    ),
                            ).andReturn()
                            .response
                    response.status shouldBe 204
                    response.contentAsString shouldBe ""
                    verify {
                        service.project(
                            "expo",
                            7,
                            registration,
                            match {
                                it.visitDate == date && it.generation == 2L && it.status == FieldVisitStatus.ACTIVE &&
                                    it.operationId == operation
                            },
                        )
                    }
                }
            }
            listOf(
                """{"visitDate":"2026-10-22","generation":0,"status":"ACTIVE","operationId":"$operation"}""",
                """{"visitDate":"2026-10-22","generation":1,"status":"ACTIVE"}""",
                """{"visitDate":"unknown","generation":1,"status":"ACTIVE","operationId":"$operation"}""",
            ).forEachIndexed { index, body ->
                `when`("projection 경계 값이 잘못되면 $index") {
                    then("400이고 서비스는 호출하지 않는다") {
                        mvc
                            .perform(put(prefix).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn()
                            .response.status shouldBe
                            400
                        verify(exactly = 0) { service.project(any(), any(), any(), any()) }
                    }
                }
            }
            `when`("취소 상태를 조회하면") {
                every { service.status(any(), any(), any(), any()) } returns
                    FieldVisitCancellationResDto(registration, 7, date, 2, false, false, FieldVisitGuardState.NONE, null)
                then("정확한 대상 echo와 NONE을 JSON으로 반환한다") {
                    val response =
                        mvc
                            .perform(
                                post("$prefix/cancellation-status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""{"phoneNumber":"fake","visitDate":"2026-10-22","generation":2}"""),
                            ).andReturn()
                            .response
                    response.status shouldBe 200
                    val json = JsonMapper.builder().build().readTree(response.contentAsString)
                    json["registrationId"].asString() shouldBe registration.toString()
                    json["participantId"].asLong() shouldBe 7L
                    json["visitDate"].asString() shouldBe "2026-10-22"
                    json["generation"].asLong() shouldBe 2L
                    json["guardState"].asString() shouldBe "NONE"
                    json.has("phoneNumber") shouldBe false
                }
            }
            `when`("acquire commit abort를 호출하면") {
                then("지정 경로와 DELETE JSON body를 받아 모두 204 empty body이다") {
                    val body = """{"visitDate":"2026-10-22","generation":2}"""
                    val acquire =
                        mvc
                            .perform(
                                put("$prefix/cancellation-guards/$operation")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""{"phoneNumber":"fake","visitDate":"2026-10-22","generation":2}"""),
                            ).andReturn()
                            .response
                    val commit =
                        mvc
                            .perform(
                                post("$prefix/cancellation-guards/$operation/commit")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body),
                            ).andReturn()
                            .response
                    val abort =
                        mvc
                            .perform(
                                delete("$prefix/cancellation-guards/$operation")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body),
                            ).andReturn()
                            .response
                    listOf(acquire.status, commit.status, abort.status) shouldBe listOf(204, 204, 204)
                    listOf(acquire.contentAsString, commit.contentAsString, abort.contentAsString) shouldBe listOf("", "", "")
                    verify { service.abort("expo", 7, registration, operation, match { it.visitDate == date && it.generation == 2L }) }
                }
            }
        }
    }
}
