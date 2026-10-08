package team.startup.expo.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import feign.FeignException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.application.ProgramApplicationResDto
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.expo.TrainingProgramResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantReqDto
import team.startup.expo.global.client.user.ResolveParticipantResDto
import team.startup.expo.global.client.user.StandardParticipantBriefResDto
import team.startup.expo.global.client.user.StandardParticipantBriefsReqDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 다른 서비스의 실제 계약(경로, 메서드, 본문, 내부 토큰 헤더, 응답 JSON)대로 Feign 클라이언트가 부르고
 * 읽는지 가짜 HTTP 서버로 확인한다. 서비스 테스트는 클라이언트를 목으로 대체하므로 이 부분은 여기서만 검증된다.
 */
class ClientContractTests : IntegrationTestSupport() {
    @Autowired
    lateinit var applicationClient: ApplicationClient

    @Autowired
    lateinit var expoClient: ExpoClient

    @Autowired
    lateinit var userClient: UserClient

    private class Received(
        val method: String,
        val path: String,
        val token: String?,
        val body: String,
    )

    @BeforeEach
    fun clear() {
        received.clear()
        notFoundPaths.clear()
        forcedStatuses.clear()
    }

    @Test
    fun `신청 서비스 신청 여부 조회는 경로와 토큰을 맞춰 부르고 응답을 읽는다`() {
        respond("/internal/program-applications/standard/7/participants/9", """{"applied":true}""")
        respond("/internal/program-applications/training/7/trainees/9", """{"applied":false}""")

        applicationClient.checkStandardApplication(7, 9) shouldBe ProgramApplicationResDto(true)
        applicationClient.checkTrainingApplication(7, 9) shouldBe ProgramApplicationResDto(false)

        received.map { it.method to it.path } shouldBe
            listOf(
                "GET" to "/internal/program-applications/standard/7/participants/9",
                "GET" to "/internal/program-applications/training/7/trainees/9",
            )
        received.map { it.token }.toSet() shouldBe setOf(APPLICATION_INTERNAL_TOKEN)
    }

    @Test
    fun `박람회 서비스 프로그램 조회는 경로와 본문을 맞추고 모르는 필드는 무시한다`() {
        respond("/internal/expo/expo-1/standard-programs/3", """{"id":3,"title":"체험"}""")
        respond(
            "/internal/expo/expo-1/training-programs/batch",
            """[{"id":5,"title":"연수","startedAt":"2026-10-07 09:00","endedAt":"2026-10-07 10:00","category":"AI"}]""",
        )

        expoClient.getStandardProgram("expo-1", 3) shouldBe StandardProgramResDto(3, "체험")
        expoClient.getTrainingPrograms("expo-1", TrainingProgramBatchReqDto(listOf(5))) shouldBe
            listOf(TrainingProgramResDto(5, "연수"))

        received[0].let { it.method to it.path } shouldBe ("GET" to "/internal/expo/expo-1/standard-programs/3")
        received[1].method shouldBe "POST"
        received[1].body shouldBe """{"programIds":[5]}"""
        received.map { it.token }.toSet() shouldBe setOf(EXPO_INTERNAL_TOKEN)
    }

    @Test
    fun `유저 서비스 이름 조회는 경로와 본문을 맞추고 404는 NotFound로 던진다`() {
        respond("/internal/standard-participants/names", """[{"participantId":1,"name":"홍길동"}]""")
        respond("/internal/trainees/names", """[{"traineeId":2,"name":"김연수"}]""")

        userClient.getStandardParticipantNames(StandardParticipantNamesReqDto("expo-1", listOf(1))) shouldBe
            listOf(StandardParticipantNameResDto(1, "홍길동"))
        userClient.getTraineeNames(TraineeNamesReqDto("expo-1", listOf(2))) shouldBe listOf(TraineeNameResDto(2, "김연수"))

        received[0].body shouldBe """{"expoId":"expo-1","participantIds":[1]}"""
        received[1].body shouldBe """{"expoId":"expo-1","traineeIds":[2]}"""
        received.map { it.token }.toSet() shouldBe setOf(USER_INTERNAL_TOKEN)

        notFoundPaths += "/internal/trainees/names"
        assertThrows<FeignException.NotFound> { userClient.getTraineeNames(TraineeNamesReqDto("expo-1", listOf(3))) }
    }

    @Test
    fun `유저 서비스 입장 기록은 전화번호 방식의 본문과 응답을 맞추고 빈 필드를 보내지 않는다`() {
        respond(
            "/internal/entries",
            """{"id":42,"name":"홍길동","phoneNumber":"01012345678","notificationPhoneNumber":"01012345678",""" +
                """"personalInformationStatus":true,"participationType":"STANDARD","occupation":"TEACHER","school":"광주초"}""",
        )

        userClient.recordEntry(RecordEntryReqDto("expo-1", "STANDARD", phoneNumber = "01012345678")) shouldBe
            RecordEntryResDto(42, "홍길동", "01012345678", true, "STANDARD", "TEACHER", "광주초", "01012345678")

        received[0].let { it.method to it.path } shouldBe ("POST" to "/internal/entries")
        received[0].token shouldBe USER_INTERNAL_TOKEN
        // `participantId`와 `code`는 `null`이라 본문에서 빠져야 한다(유저 서비스는 이 값이 있으면 그 경로를 우선한다)
        received[0].body.tree() shouldBe
            """{"expoId":"expo-1","participationType":"STANDARD","phoneNumber":"01012345678"}""".tree()
    }

    @Test
    fun `유저 서비스 입장 기록은 참가자 ID와 코드 방식의 본문을 맞추고 번호 없는 동행자 응답을 읽는다`() {
        respond(
            "/internal/entries",
            """{"id":5001,"name":"동행자","phoneNumber":null,"notificationPhoneNumber":"01077776666",""" +
                """"personalInformationStatus":true,"participationType":"STANDARD","occupation":"ELEMENTARY_STUDENT","school":null}""",
        )

        userClient.recordEntry(RecordEntryReqDto("expo-1", "STANDARD", participantId = 5001, code = "abcdefghijklmnopqrstuv")) shouldBe
            RecordEntryResDto(5001, "동행자", null, true, "STANDARD", "ELEMENTARY_STUDENT", null, "01077776666")

        // 전화번호는 null이라 보내지 않고 ID와 코드만 보낸다
        received[0].body.tree() shouldBe
            """{"expoId":"expo-1","participationType":"STANDARD","participantId":5001,"code":"abcdefghijklmnopqrstuv"}""".tree()
    }

    @Test
    fun `유저 서비스 입장 기록 응답에 문자 수신 번호 필드가 없어도 읽는다`() {
        // 필드가 추가되기 전의 응답(옛 유저 서비스)과 호환되어야 한다
        respond(
            "/internal/entries",
            """{"id":7,"name":"김연수","phoneNumber":"01099998888","personalInformationStatus":true,""" +
                """"participationType":"TRAINEE","occupation":null,"school":"광주중"}""",
        )

        userClient.recordEntry(RecordEntryReqDto("expo-1", "TRAINEE", phoneNumber = "01099998888")) shouldBe
            RecordEntryResDto(7, "김연수", "01099998888", true, "TRAINEE", null, "광주중", null)
    }

    @Test
    fun `유저 서비스 입장 기록의 404는 NotFound로 409는 Conflict로 던져진다`() {
        respond("/internal/entries", "{}")
        val request = RecordEntryReqDto("expo-1", "STANDARD", phoneNumber = "01012345678")

        forcedStatuses += 404
        assertThrows<FeignException.NotFound> { userClient.recordEntry(request) }

        forcedStatuses += 409
        assertThrows<FeignException.Conflict> { userClient.recordEntry(request) }
    }

    @Test
    fun `유저 서비스 참가자 조회는 본문과 응답을 맞춘다`() {
        respond("/internal/participants/resolve", """{"participantId":7,"participationType":"STANDARD"}""")

        userClient.resolveParticipant(ResolveParticipantReqDto("expo-1", "01012345678", "STANDARD")) shouldBe
            ResolveParticipantResDto(7, "STANDARD")

        received[0].let { it.method to it.path } shouldBe ("POST" to "/internal/participants/resolve")
        received[0].token shouldBe USER_INTERNAL_TOKEN
        received[0].body.tree() shouldBe """{"expoId":"expo-1","phoneNumber":"01012345678","participationType":"STANDARD"}""".tree()
    }

    @Test
    fun `유저 서비스 참가자 요약 조회는 본문을 맞추고 번호 없는 동행자와 문자 수신 번호 유무를 모두 읽는다`() {
        respond(
            "/internal/standard-participants/details",
            """[{"participantId":1,"name":"대표","phoneNumber":"01012345678","personalInformationStatus":true,"extra":1},""" +
                """{"participantId":2,"name":"동행","personalInformationStatus":true,"notificationPhoneNumber":"01012345678"}]""",
        )

        userClient.getStandardParticipantBriefs(StandardParticipantBriefsReqDto("expo-1", listOf(1, 2))) shouldBe
            listOf(
                StandardParticipantBriefResDto(1, "대표", "01012345678", true, null),
                StandardParticipantBriefResDto(2, "동행", null, true, "01012345678"),
            )

        received[0].let { it.method to it.path } shouldBe ("POST" to "/internal/standard-participants/details")
        received[0].token shouldBe USER_INTERNAL_TOKEN
        received[0].body.tree() shouldBe """{"expoId":"expo-1","participantIds":[1,2]}""".tree()

        notFoundPaths += "/internal/standard-participants/details"
        assertThrows<FeignException.NotFound> {
            userClient.getStandardParticipantBriefs(
                StandardParticipantBriefsReqDto("expo-1", listOf(9)),
            )
        }
    }

    @Test
    fun `유저 서비스 참가자 확인은 본문과 토큰을 맞추고 204를 정상으로, 404는 NotFound로 던진다`() {
        respond("/internal/standard-participants/verify", "")

        userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto("expo-1", 7, "codeCCCCCCCCCCCCCCCCCC"))

        received[0].let { it.method to it.path } shouldBe ("POST" to "/internal/standard-participants/verify")
        received[0].token shouldBe USER_INTERNAL_TOKEN
        received[0].body.tree() shouldBe """{"expoId":"expo-1","participantId":7,"code":"codeCCCCCCCCCCCCCCCCCC"}""".tree()

        notFoundPaths += "/internal/standard-participants/verify"
        assertThrows<FeignException.NotFound> {
            userClient.verifyStandardParticipant(
                VerifyStandardParticipantReqDto("expo-1", 7, "wrong"),
            )
        }
    }

    @Test
    fun `박람회 서비스 회차 조회는 경로와 토큰을 맞추고 UTC 시각을 읽으며 404와 409는 호출 실패로 던진다`() {
        respond(
            "/internal/expo/expo-1/preregister-sessions/5",
            """{"id":5,"expoId":"expo-1","title":"오전","startedAt":"2026-10-31T00:30:00Z","endedAt":"2026-10-31T03:30:00Z","place":"광주","capacity":100,"revision":2}""",
        )

        expoClient.getPreregisterSession("expo-1", 5) shouldBe
            PreregisterSessionResDto(5, Instant.parse("2026-10-31T00:30:00Z"), Instant.parse("2026-10-31T03:30:00Z"))

        received[0].let { it.method to it.path } shouldBe ("GET" to "/internal/expo/expo-1/preregister-sessions/5")
        received[0].token shouldBe EXPO_INTERNAL_TOKEN

        notFoundPaths += "/internal/expo/expo-1/preregister-sessions/6"
        assertThrows<FeignException.NotFound> { expoClient.getPreregisterSession("expo-1", 6) }
        forcedStatuses += 409
        assertThrows<FeignException.Conflict> { expoClient.getPreregisterSession("expo-1", 5) }
    }

    @Test
    fun `박람회 서비스 기간 조회는 경로와 토큰을 맞추고 404는 NotFound로 던진다`() {
        respond("/internal/expo/expo-1", """{"title":"박람회","startedDay":"2026-10-07","finishedDay":"2026-10-09"}""")

        expoClient.getPeriod("expo-1") shouldBe ExpoPeriodResDto("2026-10-07", "2026-10-09")

        received[0].let { it.method to it.path } shouldBe ("GET" to "/internal/expo/expo-1")
        received[0].token shouldBe EXPO_INTERNAL_TOKEN

        notFoundPaths += "/internal/expo/expo-none"
        assertThrows<FeignException.NotFound> { expoClient.getPeriod("expo-none") }
    }

    private fun String.tree() = mapper.readTree(this)

    private fun respond(
        path: String,
        json: String,
    ) {
        responses[path] = json
    }

    companion object {
        private val received = CopyOnWriteArrayList<Received>()
        private val responses = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val notFoundPaths = CopyOnWriteArrayList<String>()

        /** 다음 요청들에 순서대로 돌려줄 상태 코드. 같은 경로가 상태별로 다르게 답해야 할 때 쓴다. */
        private val forcedStatuses = ConcurrentLinkedQueue<Int>()
        private val mapper = JsonMapper.builder().build()

        private val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange -> handle(exchange) }
                start()
            }

        private fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            received += Received(exchange.requestMethod, path, exchange.requestHeaders.getFirst("X-Internal-Token"), body)

            val json = responses[path]
            val forced = forcedStatuses.poll()
            val (status, payload) =
                when {
                    forced != null -> forced to """{"status":$forced,"message":"강제 응답"}"""
                    path in notFoundPaths -> 404 to """{"status":404,"message":"없음"}"""
                    json == null -> 500 to """{"message":"unexpected path"}"""
                    else -> 200 to json
                }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun clientUrls(registry: DynamicPropertyRegistry) {
            val url = "http://127.0.0.1:${server.address.port}"
            registry.add("clients.user.url") { url }
            registry.add("clients.expo.url") { url }
            registry.add("clients.application.url") { url }
        }
    }
}
