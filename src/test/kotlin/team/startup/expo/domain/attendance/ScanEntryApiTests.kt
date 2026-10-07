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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.ResolveParticipantResDto
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
    lateinit var entryOutboxRepository: EntryOutboxRepository

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
    fun `일반 참가자 입장은 기록하고 이벤트를 남기며 교사가 아니면 명찰이 없다`() {
        entryReturns("expo-s1", standardEntry(id = 4201, occupation = "STUDENT"))

        mockMvc
            .perform(scan("expo-s1", "ROLE_STANDARD", "01012345678"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(4201))
            .andExpect(jsonPath("$.participationType").value("STANDARD"))
            .andExpect(jsonPath("$.badge").doesNotExist())

        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-s1", 4201, today) shouldBe true
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
    fun `연수자는 항상 명찰을 받고 이벤트는 남기지 않는다`() {
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

        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-t1", 7, today) shouldBe false
    }

    @Test
    fun `같은 날 두 번째 입장은 400이고 이벤트는 하나만 남는다`() {
        entryReturns("expo-dup", standardEntry(id = 4203))
        mockMvc.perform(scan("expo-dup", "ROLE_STANDARD", "01012345678")).andExpect(status().isOk)

        entryThrows("expo-dup", 409)
        resolveReturns("expo-dup", ResolveParticipantResDto(4203, "STANDARD"))
        mockMvc.perform(scan("expo-dup", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)

        entryOutboxRepository.findAll().count { it.expoId == "expo-dup" && it.participantId == 4203L } shouldBe 1
    }

    @Test
    fun `이벤트 기록이 빠진 채 이미 입장한 참가자는 다시 찍으면 이벤트가 만들어진다`() {
        entryThrows("expo-recover", 409)
        resolveReturns("expo-recover", ResolveParticipantResDto(4204, "STANDARD"))

        mockMvc.perform(scan("expo-recover", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)

        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-recover", 4204, today) shouldBe true
    }

    @Test
    fun `이벤트 복구에 실패하면 400이 아니라 재시도할 수 있는 503이다`() {
        entryThrows("expo-recover-fail", 409)
        resolveThrows("expo-recover-fail", 500)
        mockMvc.perform(scan("expo-recover-fail", "ROLE_STANDARD", "01012345678")).andExpect(status().isServiceUnavailable)

        // 저장이 실패하는 경우: 아웃박스 전화번호 컬럼(15자)을 넘는 번호
        val longPhone = "0101234567890123456"
        entryThrows("expo-recover-fail", 409, phone = longPhone)
        resolveReturns("expo-recover-fail", ResolveParticipantResDto(4300, "STANDARD"), phone = longPhone)
        mockMvc
            .perform(scan("expo-recover-fail", "ROLE_STANDARD", longPhone))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("입장 이벤트를 기록하지 못했습니다. 잠시 후 다시 시도해 주세요."))
        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-recover-fail", 4300, today) shouldBe false
    }

    @Test
    fun `삭제된 박람회에는 입장 이벤트를 다시 남기지 않는다`() {
        deletedExpoRepository.insertIfAbsent("expo-deleted", LocalDateTime.now(clock))

        // 입장 기록은 유저 서비스에 남지만 삭제된 박람회의 이벤트(전화번호 포함)는 만들지 않는다
        entryReturns("expo-deleted", standardEntry(id = 4400))
        mockMvc.perform(scan("expo-deleted", "ROLE_STANDARD", "01012345678")).andExpect(status().isOk)
        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-deleted", 4400, today) shouldBe false

        // 이미 입장한 뒤의 복구 경로도 같은 저장 경로라 이벤트를 만들지 않는다
        entryThrows("expo-deleted", 409)
        resolveReturns("expo-deleted", ResolveParticipantResDto(4401, "STANDARD"))
        mockMvc.perform(scan("expo-deleted", "ROLE_STANDARD", "01012345678")).andExpect(status().isBadRequest)
        entryOutboxRepository.existsByExpoIdAndParticipantIdAndAttendanceDate("expo-deleted", 4401, today) shouldBe false
    }

    @Test
    fun `참가자가 없으면 404이다`() {
        entryThrows("expo-s3", 404, phone = "01000000000")

        mockMvc.perform(scan("expo-s3", "ROLE_STANDARD", "01000000000")).andExpect(status().isNotFound)
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
        resolveReturns("expo-msg", ResolveParticipantResDto(4299, "STANDARD"))
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

    private fun entryThrows(
        expoId: String,
        status: Int,
        type: String = "STANDARD",
        phone: String = "01012345678",
    ) {
        doThrow(feignException(status)).`when`(userClient).recordEntry(RecordEntryReqDto(expoId, type, phone))
    }

    private fun resolveThrows(
        expoId: String,
        status: Int,
        type: String = "STANDARD",
        phone: String = "01012345678",
    ) {
        doThrow(feignException(status)).`when`(userClient).resolveParticipant(ResolveParticipantReqDto(expoId, phone, type))
    }

    private fun resolveReturns(
        expoId: String,
        response: ResolveParticipantResDto,
        type: String = "STANDARD",
        phone: String = "01012345678",
    ) {
        doReturn(response).`when`(userClient).resolveParticipant(ResolveParticipantReqDto(expoId, phone, type))
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
