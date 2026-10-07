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
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.expo.TrainingProgramResDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport
import java.net.InetSocketAddress
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
            val (status, payload) =
                when {
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
