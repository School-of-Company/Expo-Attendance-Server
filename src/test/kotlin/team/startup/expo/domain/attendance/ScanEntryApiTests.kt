package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

class ScanEntryApiTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var userClient: UserClient

    @MockitoBean
    lateinit var expoClient: ExpoClient

    @Autowired
    lateinit var deletedExpoRepository: DeletedExpoRepository

    @Autowired
    lateinit var clock: Clock

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        periodReturns(ExpoPeriodResDto(startedDay = today.minusDays(1).toString(), finishedDay = today.plusDays(1).toString()))
    }

    @Test
    fun `일반 참가자 입장은 기록하고 교사가 아니면 명찰이 없다`() {
        entryReturns("expo-s1", standardEntry(id = 4201, occupation = "STUDENT"))

        mockMvc
            .perform(scan("expo-s1", "ROLE_STANDARD", "01012345678"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(4201))
            .andExpect(jsonPath("$.participationType").value("STANDARD"))
            .andExpect(jsonPath("$.badge").doesNotExist())
    }

    @Test
    fun `교사인 일반 참가자는 명찰과 v1 형식 QR 값을 받는다`() {
        entryReturns("expo-s2", standardEntry(id = 4202, occupation = "TEACHER", school = "광주초등학교"))

        mockMvc
            .perform(scan("expo-s2", "ROLE_STANDARD", "01012345678"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.badge.name").value("홍길동"))
            .andExpect(jsonPath("$.badge.school").value("광주초등학교"))
            .andExpect(jsonPath("$.badge.qrCode").value("""{"participantId":4202,"phoneNumber":"01012345678"}"""))
    }

    @Test
    fun `예비교사도 명찰을 받고 교사가 아닌 구분은 받지 않는다`() {
        entryReturns("expo-s6", standardEntry(id = 4210, occupation = "PRE_SERVICE_TEACHER", school = "광주교육대학교"))
        mockMvc
            .perform(scan("expo-s6", "ROLE_STANDARD", "01012345678"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.badge.school").value("광주교육대학교"))
            .andExpect(jsonPath("$.badge.qrCode").value("""{"participantId":4210,"phoneNumber":"01012345678"}"""))

        listOf("SCHOOL_STAFF", "PARENT", "GENERAL", "ELEMENTARY_STUDENT").forEachIndexed { index, occupation ->
            entryReturns("expo-s7-$index", standardEntry(id = 4220L + index, occupation = occupation))
            mockMvc
                .perform(scan("expo-s7-$index", "ROLE_STANDARD", "01012345678"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.badge").doesNotExist())
        }
    }

    @Test
    fun `연수자는 항상 명찰을 받는다`() {
        entryReturns(
            "expo-t1",
            RecordEntryResDto(7, "김연수", "01099998888", true, "TRAINEE", null, "광주중학교"),
            type = "TRAINEE",
            phone = "01099998888",
        )

        mockMvc
            .perform(scan("expo-t1", "ROLE_TRAINEE", "01099998888"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participationType").value("TRAINEE"))
            .andExpect(jsonPath("$.badge.qrCode").value("""{"traineeId":7,"phoneNumber":"01099998888"}"""))
    }

    @Test
    fun `같은 날 두 번째 입장은 400이다`() {
        entryReturns("expo-dup", standardEntry(id = 4203))
        mockMvc.perform(scan("expo-dup", "ROLE_STANDARD", "01012345678")).andExpect(status().isOk)

        entryThrows("expo-dup", 409)
        mockMvc.perform(scan("expo-dup", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)
    }

    @Test
    fun `응답이 유실돼 같은 스캐너 사용자가 곧바로 다시 찍으면 처음 응답을 돌려주고 입장은 한 번만 기록한다`() {
        entryReturns("expo-retry", standardEntry(id = 4800, occupation = "TEACHER", school = "광주초등학교"))
        val first =
            mockMvc
                .perform(scan("expo-retry", "ROLE_STANDARD", "01012345678").header("X-User-Id", "staff-1"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        // 유저 서비스는 이미 입장했다고 답하지만 같은 스캐너의 재시도는 처음 응답(명찰 포함)을 받는다
        entryThrows("expo-retry", 409)
        val retry =
            mockMvc
                .perform(scan("expo-retry", "ROLE_STANDARD", "01012345678").header("X-User-Id", "staff-1"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        retry shouldBe first
        verify(userClient, times(1)).recordEntry(RecordEntryReqDto("expo-retry", "STANDARD", "01012345678"))
    }

    @Test
    fun `다른 스캐너 사용자나 스캐너를 모르는 요청은 재시도로 보지 않고 400이다`() {
        entryReturns("expo-retry2", standardEntry(id = 4801))
        mockMvc.perform(scan("expo-retry2", "ROLE_STANDARD", "01012345678").header("X-User-Id", "staff-1")).andExpect(status().isOk)

        entryThrows("expo-retry2", 409)
        mockMvc
            .perform(scan("expo-retry2", "ROLE_STANDARD", "01012345678").header("X-User-Id", "staff-2"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(scan("expo-retry2", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)
    }

    @Test
    fun `참가자 ID와 코드로 입장하면 번호 없이 기록한다`() {
        // 번호가 없는 동행자
        entryByIdReturns(
            "expo-i1",
            5001,
            "codeAAAAAAAAAAAAAAAAAA",
            RecordEntryResDto(5001, "동행자", null, true, "STANDARD", "ELEMENTARY_STUDENT", null),
        )

        mockMvc
            .perform(scanById("expo-i1", 5001, "codeAAAAAAAAAAAAAAAAAA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5001))
            .andExpect(jsonPath("$.phoneNumber").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.badge").doesNotExist())
    }

    @Test
    fun `참가자 ID와 코드로 입장한 교사의 명찰 QR은 ID와 코드다`() {
        entryByIdReturns(
            "expo-i2",
            5002,
            "codeBBBBBBBBBBBBBBBBBB",
            RecordEntryResDto(5002, "홍길동", "01012345678", true, "STANDARD", "TEACHER", "광주초등학교"),
        )

        mockMvc
            .perform(scanById("expo-i2", 5002, "codeBBBBBBBBBBBBBBBBBB"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.badge.school").value("광주초등학교"))
            .andExpect(jsonPath("$.badge.qrCode").value("""{"participantId":5002,"code":"codeBBBBBBBBBBBBBBBBBB"}"""))
    }

    @Test
    fun `코드가 틀리거나 참가자가 없으면 404이고 이미 입장했으면 400이다`() {
        doThrow(feignException(404))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto("expo-i4", "STANDARD", participantId = 5004, code = "wrongWrongWrongWrong"))
        mockMvc
            .perform(scanById("expo-i4", 5004, "wrongWrongWrongWrong"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("행사 참가자를 찾지 못 했습니다."))

        doThrow(feignException(409))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto("expo-i5", "STANDARD", participantId = 5005, code = "codeEEEEEEEEEEEEEEEEEE"))
        mockMvc
            .perform(scanById("expo-i5", 5005, "codeEEEEEEEEEEEEEEEEEE"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("이미 박람회에 입장한 유저입니다."))
    }

    @Test
    fun `일반 참가자는 번호나 ID와 코드가 필요하고 연수자는 번호가 필요하다`() {
        listOf(
            """{"authority": "ROLE_STANDARD"}""",
            """{"authority": "ROLE_STANDARD", "participantId": 1}""",
            """{"authority": "ROLE_STANDARD", "code": "abc"}""",
            """{"authority": "ROLE_STANDARD", "participantId": 0, "code": "abc"}""",
            """{"authority": "ROLE_TRAINEE", "participantId": 1, "code": "abc"}""",
            """{"authority": "ROLE_TRAINEE"}""",
        ).forEach { body ->
            mockMvc
                .perform(patch("/attendance/expo-i6").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `참가자가 없으면 404이다`() {
        entryThrows("expo-s3", 404, phone = "01000000000")

        mockMvc.perform(scan("expo-s3", "ROLE_STANDARD", "01000000000")).andExpect(status().isNotFound)
    }

    @Test
    fun `박람회 서비스가 잘못된 날짜를 주면 500이 아니라 503이다`() {
        periodReturns(ExpoPeriodResDto(startedDay = "2026/10/01", finishedDay = today.plusDays(1).toString()))

        mockMvc.perform(scan("expo-bad-date", "ROLE_STANDARD", "01012345678")).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `v1과 같은 문구로 응답한다`() {
        entryThrows("expo-msg", 404)
        mockMvc
            .perform(scan("expo-msg", "ROLE_STANDARD", "01012345678"))
            .andExpect(jsonPath("$.message").value("행사 참가자를 찾지 못 했습니다."))

        entryThrows("expo-msg", 404, type = "TRAINEE")
        mockMvc
            .perform(scan("expo-msg", "ROLE_TRAINEE", "01012345678"))
            .andExpect(jsonPath("$.message").value("연수자를 찾지 못 했습니다."))

        entryThrows("expo-msg", 409)
        mockMvc
            .perform(scan("expo-msg", "ROLE_STANDARD", "01012345678"))
            .andExpect(jsonPath("$.message").value("이미 박람회에 입장한 유저입니다."))

        periodThrows(404)
        mockMvc
            .perform(scan("expo-msg", "ROLE_STANDARD", "01012345678"))
            .andExpect(jsonPath("$.message").value("박람회를 찾지 못 했습니다."))

        periodReturns(ExpoPeriodResDto(today.plusDays(1).toString(), today.plusDays(3).toString()))
        mockMvc
            .perform(scan("expo-msg", "ROLE_STANDARD", "01012345678"))
            .andExpect(jsonPath("$.message").value("해당 박람회는 진행 중인 상태가 아닙니다."))
    }

    @Test
    fun `박람회가 없으면 404이고 진행 기간이 아니면 400이다`() {
        periodThrows(404)
        mockMvc.perform(scan("expo-none", "ROLE_STANDARD", "01012345678")).andExpect(status().isNotFound)

        periodReturns(ExpoPeriodResDto(today.plusDays(1).toString(), today.plusDays(3).toString()))
        mockMvc.perform(scan("expo-future", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)
    }

    @Test
    fun `다른 서비스 호출이 실패하면 503이다`() {
        entryThrows("expo-s4", 500)
        mockMvc.perform(scan("expo-s4", "ROLE_STANDARD", "01012345678")).andExpect(status().isServiceUnavailable)

        periodThrows(500)
        mockMvc.perform(scan("expo-s4", "ROLE_STANDARD", "01012345678")).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        mockMvc.perform(scan("expo-s5", "ROLE_ADMIN", "01012345678")).andExpect(status().isBadRequest)
        mockMvc.perform(scan("expo-s5", "ROLE_STANDARD", "")).andExpect(status().isBadRequest)
    }

    private fun entryReturns(
        expoId: String,
        response: RecordEntryResDto,
        type: String = "STANDARD",
        phone: String = "01012345678",
    ) {
        doReturn(response).`when`(userClient).recordEntry(RecordEntryReqDto(expoId, type, phone))
    }

    private fun entryByIdReturns(
        expoId: String,
        participantId: Long,
        code: String,
        response: RecordEntryResDto,
    ) {
        doReturn(response)
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(expoId, "STANDARD", participantId = participantId, code = code))
    }

    private fun scanById(
        expoId: String,
        participantId: Long,
        code: String,
    ) = patch("/attendance/$expoId")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"authority": "ROLE_STANDARD", "participantId": $participantId, "code": "$code"}""")

    private fun entryThrows(
        expoId: String,
        status: Int,
        type: String = "STANDARD",
        phone: String = "01012345678",
    ) {
        doThrow(feignException(status)).`when`(userClient).recordEntry(RecordEntryReqDto(expoId, type, phone))
    }

    private fun periodReturns(response: ExpoPeriodResDto) {
        doReturn(response).`when`(expoClient).getPeriod(anyString())
    }

    private fun periodThrows(status: Int) {
        doThrow(feignException(status)).`when`(expoClient).getPeriod(anyString())
    }

    private fun standardEntry(
        id: Long,
        occupation: String? = "STUDENT",
        school: String? = null,
    ) = RecordEntryResDto(id, "홍길동", "01012345678", true, "STANDARD", occupation, school)

    private fun feignException(status: Int): FeignException =
        FeignException.errorStatus(
            "call",
            feign.Response
                .builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.POST, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate()))
                .headers(emptyMap())
                .build(),
        )

    private fun scan(
        expoId: String,
        authority: String,
        phoneNumber: String,
    ) = patch("/attendance/$expoId")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"authority": "$authority", "phoneNumber": "$phoneNumber"}""")
}
