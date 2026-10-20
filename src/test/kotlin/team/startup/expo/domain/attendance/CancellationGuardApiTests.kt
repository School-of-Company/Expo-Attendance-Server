package team.startup.expo.domain.attendance

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.application.ProgramApplicationResDto
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.expo.TrainingProgramResDto
import team.startup.expo.global.client.user.AdminCancellationStatusReqDto
import team.startup.expo.global.client.user.AdminCancellationStatusResDto
import team.startup.expo.global.client.user.CancellationStatusReqDto
import team.startup.expo.global.client.user.CancellationStatusResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantResDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CancellationGuardApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var standardRepository: StandardProgramAttendanceRepository

    @Autowired
    private lateinit var trainingRepository: TrainingProgramAttendanceRepository

    @MockitoBean
    private lateinit var userClient: UserClient

    @MockitoBean
    private lateinit var expoClient: ExpoClient

    @MockitoBean
    private lateinit var applicationClient: ApplicationClient

    private val today get() = LocalDate.now(clock)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_cancellation_guard, tb_cancellation_aborted, tb_preregister_session, tb_deleted_expo, " +
                "tb_standard_program_attendance, tb_training_program_attendance RESTART IDENTITY CASCADE",
        )
        reset(userClient, expoClient, applicationClient)
        doReturn(ExpoPeriodResDto(today.minusDays(1).toString(), today.plusDays(1).toString())).`when`(expoClient).getPeriod(anyString())
        doReturn(RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
        // 회차 입장 시간 안의 QR 입장은 확인한 회차를 함께 보낸다(#142)
        doReturn(RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, preregisterSessionId = SESSION))
        doReturn(ResolveStandardParticipantResDto(PARTICIPANT))
            .`when`(userClient)
            .resolveStandardParticipant(ResolveStandardParticipantReqDto(EXPO, PHONE))
        status(entered = false, traineeId = null)
    }

    @Test
    fun `보호를 걸면 입장과 프로그램 스캔이 막히고 중단하면 다시 된다`() {
        acquire().andExpect(status().isNoContent)

        entryScan().andExpect(status().isConflict).andExpect(jsonPath("$.message").value("신청 취소를 처리 중입니다."))
        standardProgramScan().andExpect(status().isConflict)

        abort().andExpect(status().isNoContent)
        entryScan().andExpect(status().isOk)
    }

    @Test
    fun `확정하면 스캔은 취소된 신청으로 거절되고 재신청으로 회차가 다시 기록되면 풀린다`() {
        acquire().andExpect(status().isNoContent)
        commit().andExpect(status().isNoContent)
        // 같은 작업의 commit과 acquire를 다시 보내도 같다
        commit().andExpect(status().isNoContent)
        acquire().andExpect(status().isNoContent)

        entryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
        phoneEntryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
        // 확정된 작업은 중단할 수 없다
        abort().andExpect(status().isConflict)

        mockMvc
            .perform(
                put("/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionId":11}"""),
            ).andExpect(status().isNoContent)
        jdbcTemplate.update("DELETE FROM tb_preregister_session")
        entryScan().andExpect(status().isOk)
    }

    @Test
    fun `이미 입장했으면 409이고 보호를 남기지 않는다`() {
        status(entered = true, traineeId = null)

        acquire()
            .andExpect(
                status().isConflict,
            ).andExpect(jsonPath("$.entered").value(true))
            .andExpect(jsonPath("$.programAttended").value(false))

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
        entryScan().andExpect(status().isOk)
    }

    @Test
    fun `프로그램에 출석했으면 409이고 보호를 남기지 않는다`() {
        standardRepository.insertEntryIfAbsent(3101, PARTICIPANT, today, LocalTime.of(10, 0))

        acquire()
            .andExpect(
                status().isConflict,
            ).andExpect(jsonPath("$.entered").value(false))
            .andExpect(jsonPath("$.programAttended").value(true))

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
    }

    @Test
    fun `연결된 연수자의 연수 출석이 있으면 막고 보호 중에는 그 연수자의 연수 스캔도 막는다`() {
        status(entered = false, traineeId = TRAINEE)
        trainingRepository.insertEntryIfAbsent(3201, TRAINEE, today, LocalTime.of(10, 0))
        acquire().andExpect(status().isConflict).andExpect(jsonPath("$.programAttended").value(true))

        trainingRepository.deleteAll()
        acquire().andExpect(status().isNoContent)
        trainingScan().andExpect(status().isConflict).andExpect(jsonPath("$.message").value("신청 취소를 처리 중입니다."))
        trainingRepository.findAllByTrainingProgramId(3201).isEmpty() shouldBe true
    }

    @Test
    fun `같은 작업은 멱등이고 다른 작업이 이미 걸었으면 409이다`() {
        acquire().andExpect(status().isNoContent)
        acquire().andExpect(status().isNoContent)
        acquire(OTHER_OPERATION).andExpect(status().isConflict)
        commit(OTHER_OPERATION).andExpect(status().isConflict)
        // 다른 작업의 중단은 아무것도 하지 않는다
        abort(OTHER_OPERATION).andExpect(status().isNoContent)
        entryScan().andExpect(status().isConflict)
    }

    @Test
    fun `보호 없이 확정하면 409이고 없는 작업의 중단은 204이다`() {
        commit().andExpect(status().isConflict)
        abort().andExpect(status().isNoContent)
    }

    @Test
    fun `유저 서비스가 모르면 404이고 보호를 풀며 장애면 503이고 보호를 둔 채 같은 작업의 재시도로 이어진다`() {
        doThrow(feignException(404)).`when`(userClient).getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, PARTICIPANT))
        acquire().andExpect(status().isNotFound)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L

        doThrow(feignException(500)).`when`(userClient).getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, PARTICIPANT))
        acquire().andExpect(status().isServiceUnavailable)
        entryScan().andExpect(status().isConflict)

        status(entered = false, traineeId = null)
        acquire().andExpect(status().isNoContent)
    }

    @Test
    fun `진행 중인 입장 스캔이 끝난 뒤에 보호가 걸리고 그 뒤의 스캔은 거절된다`() {
        val entering = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entering.countDown()
            release.await(10, TimeUnit.SECONDS)
            RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null)
        }.`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))

        val executor = Executors.newFixedThreadPool(2)
        val scan = executor.submit<Int> { entryScan().andReturn().response.status }
        entering.await(10, TimeUnit.SECONDS) shouldBe true
        val acquiring = executor.submit<Int> { acquire().andReturn().response.status }

        // 스캔이 끝나기 전에는 보호가 걸리지 않는다
        Thread.sleep(500)
        acquiring.isDone shouldBe false
        release.countDown()

        scan.get(10, TimeUnit.SECONDS) shouldBe 200
        acquiring.get(10, TimeUnit.SECONDS) shouldBe 204
        executor.shutdown()
        entryScan().andExpect(status().isConflict)
    }

    @Test
    fun `진행 중인 전화번호 입장이 끝나야 취소 보호가 걸리고 이후 번호 입장은 거절된다`() {
        val entering = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entering.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            RecordEntryResDto(PARTICIPANT, "홍길동", PHONE, true, "STANDARD", "STUDENT", null)
        }.`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", phoneNumber = PHONE))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val scan = executor.submit<Int> { phoneEntryScan().andReturn().response.status }
            entering.await(10, TimeUnit.SECONDS) shouldBe true
            val acquiring = executor.submit<Int> { acquire().andReturn().response.status }
            Thread.sleep(500)
            acquiring.isDone shouldBe false
            release.countDown()
            scan.get(10, TimeUnit.SECONDS) shouldBe 200
            acquiring.get(10, TimeUnit.SECONDS) shouldBe 204
            phoneEntryScan().andExpect(status().isConflict).andExpect(jsonPath("$.message").value("신청 취소를 처리 중입니다."))
            abort().andExpect(status().isNoContent)
            phoneEntryScan().andExpect(status().isOk)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun phoneEntryScan(): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","phoneNumber":"$PHONE"}"""),
        )

    @Test
    fun `서로 다른 참가자의 스캔은 서로 막지 않는다`() {
        val entering = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entering.countDown()
            release.await(10, TimeUnit.SECONDS)
            RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null)
        }.`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))
        doReturn(RecordEntryResDto(OTHER_PARTICIPANT, "김철수", "01099999999", true, "STANDARD", "STUDENT", null))
            .`when`(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = OTHER_PARTICIPANT, code = CODE))

        val executor = Executors.newFixedThreadPool(1)
        val first = executor.submit<Int> { entryScan().andReturn().response.status }
        entering.await(10, TimeUnit.SECONDS) shouldBe true

        // 첫 스캔이 락을 쥔 채 기다리는 동안 다른 참가자의 취소 보호와 스캔은 바로 끝난다
        acquire(participantId = OTHER_PARTICIPANT).andExpect(status().isNoContent)
        entryScan(OTHER_PARTICIPANT).andExpect(status().isConflict)
        release.countDown()
        first.get(10, TimeUnit.SECONDS) shouldBe 200
        executor.shutdown()
    }

    @Test
    fun `내부 토큰이 필요하고 입력을 검증하며 박람회를 삭제하면 보호도 지운다`() {
        mockMvc
            .perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("""{"phoneNumber":"$PHONE"}"""))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("$PATH/commit")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized)

        listOf("""{}""", """{"phoneNumber":""}""", """{"phoneNumber":"${"1".repeat(21)}"}""").forEach {
            mockMvc
                .perform(put(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }
        acquire(operationId = "bad id!").andExpect(status().isBadRequest)

        acquire().andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 1L
        mockMvc
            .perform(delete("/internal/expos/$EXPO").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
        acquire().andExpect(status().isNotFound)
    }

    @Test
    fun `중단된 작업의 지연된 acquire는 보호를 다시 걸지 못하고 새 operationId는 걸 수 있다`() {
        // acquire가 도착하기 전에 abort가 먼저 끝난 경우
        abort().andExpect(status().isNoContent)
        acquire().andExpect(status().isConflict)
        entryScan().andExpect(status().isOk)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L

        // 보호를 걸었다가 중단한 뒤 같은 acquire가 다시 도착한 경우, 같은 abort를 다시 보내도 같다
        acquire(OTHER_OPERATION).andExpect(status().isNoContent)
        abort(OTHER_OPERATION).andExpect(status().isNoContent)
        abort(OTHER_OPERATION).andExpect(status().isNoContent)
        acquire(OTHER_OPERATION).andExpect(status().isConflict)
        entryScan().andExpect(status().isOk)

        acquire("op-0003").andExpect(status().isNoContent)
        entryScan().andExpect(status().isConflict)
    }

    @Test
    fun `프로그램 스캔이 신청 확인과 기록을 마칠 때까지 취소 보호가 기다리고 그 뒤에는 출석이 있어 거절된다`() {
        val checking = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        doReturn(StandardProgramResDto(3101, "프로그램")).`when`(expoClient).getStandardProgram(EXPO, 3101)
        doAnswer {
            checking.countDown()
            proceed.await(10, TimeUnit.SECONDS)
            ProgramApplicationResDto(true)
        }.`when`(applicationClient).checkStandardApplication(3101, PARTICIPANT)

        val executor = Executors.newFixedThreadPool(2)
        val scan = executor.submit<Int> { programScanRequest().andReturn().response.status }
        // 스캔이 applied=true를 읽는 도중에 취소가 acquire를 시도한다
        checking.await(10, TimeUnit.SECONDS) shouldBe true
        val acquiring = executor.submit<Int> { acquire().andReturn().response.status }

        Thread.sleep(500)
        acquiring.isDone shouldBe false
        proceed.countDown()

        scan.get(10, TimeUnit.SECONDS) shouldBe 200
        // 스캔이 끝난 뒤 acquire는 이미 출석한 참가자라 취소를 막는다
        acquiring.get(10, TimeUnit.SECONDS) shouldBe 409
        standardRepository.findAllByStandardProgramId(3101).size shouldBe 1
        executor.shutdown()
    }

    @Test
    fun `취소가 끝난 뒤 들어온 프로그램 스캔은 최신 신청 상태를 읽어 출석을 기록하지 않는다`() {
        acquire().andExpect(status().isNoContent)
        // 로컬 신청 취소가 끝난 뒤 abort로 보호를 풀면 신청 확인은 false다
        abort().andExpect(status().isNoContent)
        doReturn(StandardProgramResDto(3101, "프로그램")).`when`(expoClient).getStandardProgram(EXPO, 3101)
        doReturn(ProgramApplicationResDto(false)).`when`(applicationClient).checkStandardApplication(3101, PARTICIPANT)

        programScanRequest().andExpect(status().isNotFound)

        standardRepository.findAllByStandardProgramId(3101).isEmpty() shouldBe true
    }

    @Test
    fun `취소 확정 전에 더 새로운 회차 PUT이 먼저 적용되면 확정 대신 보호를 풀어 새 신청이 입장한다`() {
        sessionValid()
        acquire().andExpect(status().isNoContent)
        // 취소 DELETE(revision 4)보다 새로운 재신청 PUT(revision 5)이 보호 중에 먼저 도착한다
        putSession(5).andExpect(status().isNoContent)
        entryScan().andExpect(status().isConflict)

        commit(revision = 4).andExpect(status().isNoContent)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
        entryScan().andExpect(status().isOk)
        verify(userClient)
            .recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE, preregisterSessionId = SESSION))
        // 밀려난 취소의 지연된 acquire는 거절된다
        acquire().andExpect(status().isConflict)
    }

    @Test
    fun `해제로 끝난 commit을 같은 operationId로 다시 보내도 204이고 abort 뒤의 commit은 409이다`() {
        sessionValid()
        acquire().andExpect(status().isNoContent)
        putSession(5).andExpect(status().isNoContent)
        commit(revision = 4).andExpect(status().isNoContent)
        // 응답이 유실되어 재시도해도 같다
        commit(revision = 4).andExpect(status().isNoContent)
        acquire().andExpect(status().isConflict)

        // abort로 끝난 작업의 commit은 그대로 409다
        acquire(OTHER_OPERATION).andExpect(status().isNoContent)
        abort(OTHER_OPERATION).andExpect(status().isNoContent)
        commit(OTHER_OPERATION).andExpect(status().isConflict)
    }

    @Test
    fun `취소가 확정된 뒤 늦게 도착한 오래된 PUT은 보호를 풀지 못하고 더 새로운 PUT만 푼다`() {
        sessionValid()
        acquire().andExpect(status().isNoContent)
        commit(revision = 4).andExpect(status().isNoContent)

        // 취소보다 오래된(또는 같은) revision의 PUT이 늦게 도착한다
        putSession(3).andExpect(status().isNoContent)
        putSession(4).andExpect(status().isNoContent)
        entryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard WHERE state = 'COMMITTED'", Long::class.java) shouldBe 1L

        putSession(5).andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
    }

    @Test
    fun `revision 없이 확정한 취소는 기존처럼 새 회차 PUT이 오면 풀린다`() {
        sessionValid()
        acquire().andExpect(status().isNoContent)
        commit().andExpect(status().isNoContent)

        putSession(2).andExpect(status().isNoContent)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
    }

    @Test
    fun `취소 상태 조회는 읽기 전용으로 입장·프로그램 출석·보호 상태를 돌려주고 아무것도 바꾸지 않는다`() {
        status(entered = false, traineeId = TRAINEE)
        cancellationStatus()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(PARTICIPANT))
            .andExpect(jsonPath("$.entered").value(false))
            .andExpect(jsonPath("$.programAttended").value(false))
            .andExpect(jsonPath("$.traineeLinked").value(true))
            .andExpect(jsonPath("$.linkedTraineeId").value(TRAINEE))
            .andExpect(jsonPath("$.guardState").doesNotExist())

        // 연결된 연수자의 연수 출석도 참가자의 프로그램 출석으로 센다
        trainingRepository.insertEntryIfAbsent(3201, TRAINEE, today, LocalTime.of(10, 0))
        cancellationStatus().andExpect(jsonPath("$.programAttended").value(true))
        trainingRepository.deleteAll()

        status(entered = true, traineeId = null)
        cancellationStatus().andExpect(jsonPath("$.entered").value(true)).andExpect(jsonPath("$.traineeLinked").value(false))

        status(entered = false, traineeId = null)
        standardRepository.insertEntryIfAbsent(3101, PARTICIPANT, today, LocalTime.of(10, 0))
        cancellationStatus().andExpect(jsonPath("$.programAttended").value(true))

        acquire().andExpect(status().isConflict)
        standardRepository.deleteAll()
        acquire().andExpect(status().isNoContent)
        cancellationStatus().andExpect(jsonPath("$.guardState").value("HELD"))
        commit().andExpect(status().isNoContent)
        cancellationStatus().andExpect(jsonPath("$.guardState").value("COMMITTED"))

        // 조회는 보호를 만들거나 바꾸지 않는다
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 1L
    }

    @Test
    fun `취소 상태 조회는 유저가 모르는 쌍은 404, 장애는 503이고 내부 토큰과 전화번호가 필요하다`() {
        doThrow(feignException(404)).`when`(userClient).getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, PARTICIPANT))
        cancellationStatus().andExpect(status().isNotFound)

        doThrow(feignException(500)).`when`(userClient).getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, PARTICIPANT))
        cancellationStatus().andExpect(status().isServiceUnavailable)

        mockMvc
            .perform(post(STATUS_PATH).contentType(MediaType.APPLICATION_JSON).content("""{"phoneNumber":"$PHONE"}"""))
            .andExpect(status().isUnauthorized)
        listOf("""{}""", """{"phoneNumber":""}""", """{"phoneNumber":"${"1".repeat(21)}"}""").forEach {
            mockMvc
                .perform(post(STATUS_PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }
    }

    private fun sessionValid() {
        doReturn(PreregisterSessionResDto(SESSION, Instant.now(clock), Instant.now(clock).plusSeconds(3 * 3600)))
            .`when`(expoClient)
            .getPreregisterSession(EXPO, SESSION)
    }

    private fun putSession(revision: Long): ResultActions =
        mockMvc.perform(
            put("/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId":$SESSION,"revision":$revision}"""),
        )

    @Test
    fun `취소 뒤 현장 재등록하면 오래된 취소만 풀려 회차 시간 확인 없이 입장하고 늦게 온 오래된 취소는 다시 막지 못한다`() {
        // 회차 응답을 주지 않으므로 회차 시간을 확인하면 실패한다
        putSession(2).andExpect(status().isNoContent)
        acquire().andExpect(status().isNoContent)
        deleteSession(3).andExpect(status().isNoContent)
        commit(revision = 3).andExpect(status().isNoContent)
        entryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))

        // 취소보다 오래된(같은) 재등록은 풀지 못한다
        fieldRegister(3).andExpect(status().isNoContent)
        entryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))

        fieldRegister(4).andExpect(status().isNoContent)
        fieldRegister(4).andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
        entryScan().andExpect(status().isOk)

        // 늦게 다시 온 같은 취소 통지는 아무것도 바꾸지 않는다. 풀린 보호의 확정 재전송은 409이고 신청 서비스는 abort로 끝낸다
        deleteSession(3).andExpect(status().isNoContent)
        commit(revision = 3).andExpect(status().isConflict)
        entryScan().andExpect(status().isOk)

        // 재등록 뒤의 새 취소는 다시 막는다
        deleteSession(5).andExpect(status().isNoContent)
        entryScan().andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("취소된 신청입니다."))

        mockMvc
            .perform(put(FIELD_PATH).contentType(MediaType.APPLICATION_JSON).content("""{"revision":6}"""))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(put(FIELD_PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `취소 보호 확정보다 현장 재등록이 먼저 도착하면 늦은 확정은 보호를 풀고 입장한다`() {
        putSession(2).andExpect(status().isNoContent)
        acquire().andExpect(status().isNoContent)
        deleteSession(3).andExpect(status().isNoContent)
        fieldRegister(4).andExpect(status().isNoContent)
        entryScan().andExpect(status().isConflict)

        commit(revision = 3).andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
        entryScan().andExpect(status().isOk)
    }

    private fun fieldRegister(revision: Long): ResultActions =
        mockMvc.perform(
            put(FIELD_PATH)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"revision":$revision}"""),
        )

    private fun deleteSession(revision: Long): ResultActions =
        mockMvc.perform(
            delete("/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session?sessionId=$SESSION&revision=$revision")
                .header("X-Internal-Token", INTERNAL_TOKEN),
        )

    private fun cancellationStatus(): ResultActions =
        mockMvc.perform(
            post(STATUS_PATH)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"$PHONE"}"""),
        )

    @Test
    fun `관리자 취소 보호는 전화번호 없이 걸리고 스캔을 막으며 abort로 풀린다`() {
        adminStatus(entered = false, traineeId = null)

        adminAcquire().andExpect(status().isNoContent)
        entryScan().andExpect(status().isConflict).andExpect(jsonPath("$.message").value("신청 취소를 처리 중입니다."))
        standardProgramScan().andExpect(status().isConflict)

        // 프로그램 신청 하나만 취소하는 흐름: 로컬 취소 뒤 abort로 끝낸다(commit하지 않는다)
        abort().andExpect(status().isNoContent)
        entryScan().andExpect(status().isOk)
        // 중단된 operationId의 지연된 acquire는 거절되고, 새 operationId는 다시 걸 수 있다
        adminAcquire().andExpect(status().isConflict)
        adminAcquire("op-0009").andExpect(status().isNoContent)
    }

    @Test
    fun `관리자 취소 보호도 이미 입장했거나 출석했으면 409이고 보호를 남기지 않는다`() {
        adminStatus(entered = true, traineeId = null)
        adminAcquire().andExpect(status().isConflict).andExpect(jsonPath("$.entered").value(true))
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L

        adminStatus(entered = false, traineeId = TRAINEE)
        trainingRepository.insertEntryIfAbsent(3201, TRAINEE, today, LocalTime.of(10, 0))
        adminAcquire().andExpect(status().isConflict).andExpect(jsonPath("$.programAttended").value(true))
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L
    }

    @Test
    fun `관리자 취소 보호는 유저가 모르면 404, 장애면 503이고 내부 토큰과 operationId를 검증한다`() {
        doThrow(feignException(404)).`when`(userClient).getAdminCancellationStatus(AdminCancellationStatusReqDto(EXPO, PARTICIPANT))
        adminAcquire().andExpect(status().isNotFound)
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_cancellation_guard", Long::class.java) shouldBe 0L

        doThrow(feignException(500)).`when`(userClient).getAdminCancellationStatus(AdminCancellationStatusReqDto(EXPO, PARTICIPANT))
        adminAcquire().andExpect(status().isServiceUnavailable)
        entryScan().andExpect(status().isConflict)

        mockMvc.perform(put(ADMIN_PATH)).andExpect(status().isUnauthorized)
        adminAcquire(operationId = "bad id!").andExpect(status().isBadRequest)
    }

    @Test
    fun `관리자 취소 보호는 진행 중인 입장 스캔이 끝난 뒤에 걸린다`() {
        adminStatus(entered = false, traineeId = null)
        val entering = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entering.countDown()
            release.await(10, TimeUnit.SECONDS)
            RecordEntryResDto(PARTICIPANT, "홍길동", "01012345678", true, "STANDARD", "STUDENT", null)
        }.`when`(userClient).recordEntry(RecordEntryReqDto(EXPO, "STANDARD", participantId = PARTICIPANT, code = CODE))

        val executor = Executors.newFixedThreadPool(2)
        val scan = executor.submit<Int> { entryScan().andReturn().response.status }
        entering.await(10, TimeUnit.SECONDS) shouldBe true
        val acquiring = executor.submit<Int> { adminAcquire().andReturn().response.status }

        Thread.sleep(500)
        acquiring.isDone shouldBe false
        release.countDown()

        scan.get(10, TimeUnit.SECONDS) shouldBe 200
        acquiring.get(10, TimeUnit.SECONDS) shouldBe 204
        executor.shutdown()
        entryScan().andExpect(status().isConflict)
    }

    private fun adminStatus(
        entered: Boolean,
        traineeId: Long?,
    ) {
        doReturn(AdminCancellationStatusResDto(PARTICIPANT, entered, traineeId != null, traineeId))
            .`when`(userClient)
            .getAdminCancellationStatus(AdminCancellationStatusReqDto(EXPO, PARTICIPANT))
    }

    private fun adminAcquire(operationId: String = OPERATION): ResultActions =
        mockMvc.perform(
            put("/internal/expos/$EXPO/participants/$PARTICIPANT/admin-cancellation-guard/$operationId")
                .header("X-Internal-Token", INTERNAL_TOKEN),
        )

    private fun programScanRequest(): ResultActions =
        mockMvc.perform(
            patch("/attendance/standard/3101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$EXPO","participantId":$PARTICIPANT,"code":"$CODE"}"""),
        )

    private fun status(
        entered: Boolean,
        traineeId: Long?,
    ) {
        doReturn(CancellationStatusResDto(PARTICIPANT, true, entered, traineeId != null, traineeId))
            .`when`(userClient)
            .getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, PARTICIPANT))
        doReturn(CancellationStatusResDto(OTHER_PARTICIPANT, true, false, false, null))
            .`when`(userClient)
            .getCancellationStatus(CancellationStatusReqDto(EXPO, PHONE, OTHER_PARTICIPANT))
    }

    private fun acquire(
        operationId: String = OPERATION,
        participantId: Long = PARTICIPANT,
    ): ResultActions =
        mockMvc.perform(
            put("/internal/expos/$EXPO/participants/$participantId/cancellation-guard/$operationId")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"$PHONE"}"""),
        )

    private fun commit(
        operationId: String = OPERATION,
        revision: Long? = null,
    ): ResultActions {
        val request =
            post("/internal/expos/$EXPO/participants/$PARTICIPANT/cancellation-guard/$operationId/commit")
                .header("X-Internal-Token", INTERNAL_TOKEN)
        return mockMvc.perform(
            if (revision == null) request else request.contentType(MediaType.APPLICATION_JSON).content("""{"revision":$revision}"""),
        )
    }

    private fun abort(operationId: String = OPERATION): ResultActions =
        mockMvc.perform(
            delete("/internal/expos/$EXPO/participants/$PARTICIPANT/cancellation-guard/$operationId")
                .header("X-Internal-Token", INTERNAL_TOKEN),
        )

    private fun entryScan(participantId: Long = PARTICIPANT): ResultActions =
        mockMvc.perform(
            patch("/attendance/$EXPO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authority":"ROLE_STANDARD","participantId":$participantId,"code":"$CODE"}"""),
        )

    private fun standardProgramScan(): ResultActions {
        doReturn(StandardProgramResDto(3101, "프로그램")).`when`(expoClient).getStandardProgram(EXPO, 3101)
        doReturn(ProgramApplicationResDto(true)).`when`(applicationClient).checkStandardApplication(3101, PARTICIPANT)
        return mockMvc.perform(
            patch("/attendance/standard/3101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$EXPO","participantId":$PARTICIPANT,"code":"$CODE"}"""),
        )
    }

    private fun trainingScan(): ResultActions {
        doReturn(listOf(TraineeNameResDto(TRAINEE, "김연수"))).`when`(userClient).getTraineeNames(TraineeNamesReqDto(EXPO, listOf(TRAINEE)))
        doReturn(listOf(TrainingProgramResDto(3201, "연수")))
            .`when`(expoClient)
            .getTrainingPrograms(EXPO, TrainingProgramBatchReqDto(listOf(3201)))
        doReturn(ProgramApplicationResDto(true)).`when`(applicationClient).checkTrainingApplication(3201, TRAINEE)
        return mockMvc.perform(
            patch("/attendance/training/3201")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$EXPO","traineeId":$TRAINEE}"""),
        )
    }

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

    private companion object {
        const val EXPO = "0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10"
        const val PARTICIPANT = 7001L
        const val OTHER_PARTICIPANT = 7002L
        const val TRAINEE = 9001L
        const val CODE = "codePPPPPPPPPPPPPPPPPP"
        const val PHONE = "01012345678"
        const val OPERATION = "op-0001"
        const val OTHER_OPERATION = "op-0002"
        const val PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/cancellation-guard/$OPERATION"
        const val ADMIN_PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/admin-cancellation-guard/$OPERATION"
        const val STATUS_PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/cancellation-status"
        const val SESSION = 11L
        const val FIELD_PATH = "/internal/expos/$EXPO/participants/$PARTICIPANT/preregister-session/field-registration"
    }
}
