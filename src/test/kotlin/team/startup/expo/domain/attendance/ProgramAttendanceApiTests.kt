package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.application.ProgramApplicationResDto
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.expo.TrainingProgramResDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.LocalDate

class ProgramAttendanceApiTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var standardRepository: StandardProgramAttendanceRepository

    @Autowired
    lateinit var trainingRepository: TrainingProgramAttendanceRepository

    @Autowired
    lateinit var clock: Clock

    @MockitoBean
    lateinit var userClient: UserClient

    @MockitoBean
    lateinit var expoClient: ExpoClient

    @MockitoBean
    lateinit var applicationClient: ApplicationClient

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        periodReturns(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString()))
    }

    @Test
    fun `일반 프로그램은 첫 스캔에 입실하고 두 번째에 퇴실하며 세 번째는 400이다`() {
        standardAllowed(expoId = "expo-a", programId = 1001, participantId = 501)

        mockMvc.perform(scanStandard(1001, "expo-a", 501)).andExpect(status().isOk)
        val entered = standardRepository.findByParticipantIdAndStandardProgramId(501, 1001)!!
        entered.leaveTime shouldBe null
        entered.attendanceDate shouldBe today

        mockMvc.perform(scanStandard(1001, "expo-a", 501)).andExpect(status().isOk)
        (standardRepository.findByParticipantIdAndStandardProgramId(501, 1001)!!.leaveTime != null) shouldBe true

        mockMvc
            .perform(scanStandard(1001, "expo-a", 501))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("이미 프로그램을 퇴실한 유저입니다."))
    }

    @Test
    fun `연수 프로그램도 같은 순서로 입실과 퇴실을 기록한다`() {
        trainingAllowed(expoId = "expo-b", programId = 2001, traineeId = 601)

        mockMvc.perform(scanTraining(2001, "expo-b", 601)).andExpect(status().isOk)
        trainingRepository.findByTraineeIdAndTrainingProgramId(601, 2001)!!.leaveTime shouldBe null

        mockMvc.perform(scanTraining(2001, "expo-b", 601)).andExpect(status().isOk)
        (trainingRepository.findByTraineeIdAndTrainingProgramId(601, 2001)!!.leaveTime != null) shouldBe true

        mockMvc.perform(scanTraining(2001, "expo-b", 601)).andExpect(status().isBadRequest)
    }

    @Test
    fun `신청하지 않았으면 404이고 기록하지 않는다`() {
        standardAllowed("expo-c", 1002, 502)
        doReturn(ProgramApplicationResDto(false)).`when`(applicationClient).checkStandardApplication(1002, 502)

        mockMvc
            .perform(scanStandard(1002, "expo-c", 502))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("일반 프로그램을 참가 중인 유저를 찾지 못 했습니다."))

        standardRepository.findByParticipantIdAndStandardProgramId(502, 1002) shouldBe null
    }

    @Test
    fun `참가자나 프로그램이 없으면 404이다`() {
        standardAllowed("expo-d", 1003, 503)
        doThrow(feignException(404)).`when`(userClient).getStandardParticipantNames(StandardParticipantNamesReqDto("expo-d", listOf(503)))
        mockMvc
            .perform(scanStandard(1003, "expo-d", 503))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("행사 참가자를 찾지 못 했습니다."))

        standardAllowed("expo-d", 1003, 504)
        doThrow(feignException(404)).`when`(expoClient).getStandardProgram("expo-d", 1003)
        mockMvc
            .perform(scanStandard(1003, "expo-d", 504))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("일반 프로그램을 찾지 못 했습니다."))

        trainingAllowed("expo-d", 2002, 604)
        doThrow(feignException(404)).`when`(expoClient).getTrainingPrograms("expo-d", TrainingProgramBatchReqDto(listOf(2002)))
        mockMvc
            .perform(scanTraining(2002, "expo-d", 604))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("연수 프로그램을 찾지 못했습니다."))

        trainingAllowed("expo-d", 2003, 605)
        doThrow(feignException(404)).`when`(userClient).getTraineeNames(TraineeNamesReqDto("expo-d", listOf(605)))
        mockMvc
            .perform(scanTraining(2003, "expo-d", 605))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("연수자를 찾지 못 했습니다."))
    }

    @Test
    fun `박람회가 진행 중이 아니면 400이다`() {
        standardAllowed("expo-e", 1004, 505)
        periodReturns(ExpoPeriodResDto(today.plusDays(1).toString(), today.plusDays(3).toString()))

        mockMvc
            .perform(scanStandard(1004, "expo-e", 505))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("해당 박람회는 진행 중인 상태가 아닙니다."))
    }

    @Test
    fun `다른 서비스 호출이 실패하면 503이다`() {
        standardAllowed("expo-f", 1005, 506)
        doThrow(feignException(500)).`when`(applicationClient).checkStandardApplication(1005, 506)

        mockMvc.perform(scanStandard(1005, "expo-f", 506)).andExpect(status().isServiceUnavailable)
        standardRepository.findByParticipantIdAndStandardProgramId(506, 1005) shouldBe null
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        mockMvc.perform(rawStandard(1006, """{"participantId": 1}""")).andExpect(status().isBadRequest)
        mockMvc.perform(rawStandard(1006, """{"expoId": "expo-g"}""")).andExpect(status().isBadRequest)
        mockMvc.perform(rawStandard(1006, """{"expoId": "expo-g", "participantId": 0}""")).andExpect(status().isBadRequest)
        mockMvc.perform(rawTraining(2006, """{"expoId": "expo-g"}""")).andExpect(status().isBadRequest)
    }

    @Test
    fun `프로그램별 출석 시간 조회는 내부 토큰이 필요하고 HH mm 형식으로 돌려준다`() {
        standardAllowed("expo-h", 1007, 507)
        mockMvc.perform(scanStandard(1007, "expo-h", 507)).andExpect(status().isOk)
        mockMvc.perform(scanStandard(1007, "expo-h", 507)).andExpect(status().isOk)
        standardAllowed("expo-h", 1007, 508)
        mockMvc.perform(scanStandard(1007, "expo-h", 508)).andExpect(status().isOk)
        trainingAllowed("expo-h", 2007, 607)
        mockMvc.perform(scanTraining(2007, "expo-h", 607)).andExpect(status().isOk)

        mockMvc.perform(get("/internal/program-attendances/standard/1007")).andExpect(status().isUnauthorized)

        mockMvc
            .perform(get("/internal/program-attendances/standard/1007").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(
                jsonPath(
                    "$[?(@.participantId==507)].entryTime",
                ).value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2}"))),
            ).andExpect(
                jsonPath(
                    "$[?(@.participantId==507)].leaveTime",
                ).value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2}"))),
            ).andExpect(
                jsonPath("$[?(@.participantId==508)].leaveTime").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())),
            )

        mockMvc
            .perform(get("/internal/program-attendances/training/2007").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].traineeId").value(607))

        mockMvc
            .perform(get("/internal/program-attendances/standard/9999").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    private fun standardAllowed(
        expoId: String,
        programId: Long,
        participantId: Long,
    ) {
        doReturn(listOf(StandardParticipantNameResDto(participantId, "홍길동")))
            .`when`(userClient)
            .getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, listOf(participantId)))
        doReturn(StandardProgramResDto(programId, "프로그램")).`when`(expoClient).getStandardProgram(expoId, programId)
        doReturn(ProgramApplicationResDto(true)).`when`(applicationClient).checkStandardApplication(programId, participantId)
    }

    private fun trainingAllowed(
        expoId: String,
        programId: Long,
        traineeId: Long,
    ) {
        doReturn(listOf(TraineeNameResDto(traineeId, "김연수")))
            .`when`(userClient)
            .getTraineeNames(TraineeNamesReqDto(expoId, listOf(traineeId)))
        doReturn(listOf(TrainingProgramResDto(programId, "연수")))
            .`when`(expoClient)
            .getTrainingPrograms(expoId, TrainingProgramBatchReqDto(listOf(programId)))
        doReturn(ProgramApplicationResDto(true)).`when`(applicationClient).checkTrainingApplication(programId, traineeId)
    }

    private fun periodReturns(period: ExpoPeriodResDto) {
        doReturn(period).`when`(expoClient).getPeriod(org.mockito.ArgumentMatchers.anyString())
    }

    private fun feignException(status: Int): FeignException =
        FeignException.errorStatus(
            "call",
            feign.Response
                .builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "http://x", emptyMap(), null, Charsets.UTF_8, RequestTemplate()))
                .headers(emptyMap())
                .build(),
        )

    private fun scanStandard(
        programId: Long,
        expoId: String,
        participantId: Long,
    ) = rawStandard(programId, """{"expoId": "$expoId", "participantId": $participantId, "phoneNumber": "01012345678"}""")

    private fun scanTraining(
        programId: Long,
        expoId: String,
        traineeId: Long,
    ) = rawTraining(programId, """{"expoId": "$expoId", "traineeId": $traineeId}""")

    private fun rawStandard(
        programId: Long,
        body: String,
    ) = patch("/attendance/standard/$programId").contentType(MediaType.APPLICATION_JSON).content(body)

    private fun rawTraining(
        programId: Long,
        body: String,
    ) = patch("/attendance/training/$programId").contentType(MediaType.APPLICATION_JSON).content(body)
}
