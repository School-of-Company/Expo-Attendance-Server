package team.startup.expo.client

import com.sun.net.httpserver.HttpServer
import feign.Feign
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.cloud.openfeign.support.SpringMvcContract
import team.startup.expo.global.client.user.ParticipantEntryStatusReqDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.UserClient
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FieldVisitFakeProviderSpec :
    BehaviorSpec({
        given("User 날짜별 entry fakeprovider") {
            `when`("FIELD entry와 날짜별 status를 HTTP로 호출하면") {
                then("실제 Feign 경로·날짜·세대와 echo를 보존한다") {
                    val mapper = JsonMapper.builder().findAndAddModules().build()
                    val received = CopyOnWriteArrayList<Pair<String, String>>()
                    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                    server.createContext("/internal/") { exchange ->
                        val path = exchange.requestURI.path
                        received.add(path to exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
                        val body =
                            if (path.endsWith("entry-status")) {
                                """{"participantId":7,"attendanceDate":"2026-10-22","entered":true}"""
                            } else {
                                """{"id":7,"name":"tester","phoneNumber":null,"personalInformationStatus":true,"participationType":"STANDARD","occupation":null,"school":null}"""
                            }
                        val bytes = body.toByteArray()
                        exchange.responseHeaders.set("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                        exchange.close()
                    }
                    server.start()
                    try {
                        val client =
                            Feign
                                .builder()
                                .contract(SpringMvcContract())
                                .encoder { body, _, template -> template.body(mapper.writeValueAsBytes(body), Charsets.UTF_8) }
                                .decoder {
                                    response,
                                    type,
                                    ->
                                    response.body().asInputStream().use { mapper.readValue(it, mapper.constructType(type)) }
                                }.target(UserClient::class.java, "http://127.0.0.1:${server.address.port}")
                        val date = LocalDate.of(2026, 10, 22)
                        val registration = UUID.randomUUID()
                        val entry =
                            client.recordEntry(
                                RecordEntryReqDto(
                                    "expo",
                                    "STANDARD",
                                    participantId = 7,
                                    code = "fake-code",
                                    attendanceDate = date,
                                    fieldVisitRegistrationId = registration,
                                    fieldVisitGeneration = 2,
                                ),
                            )
                        entry.id shouldBe 7L
                        val status = client.getEntryStatus(ParticipantEntryStatusReqDto("expo", 7, date))
                        status.participantId shouldBe 7L
                        status.attendanceDate shouldBe date
                        status.entered shouldBe true
                        received.map { it.first } shouldBe listOf("/internal/entries", "/internal/standard-participants/entry-status")
                        val json = mapper.readTree(received[0].second)
                        json["attendanceDate"].asString() shouldBe "2026-10-22"
                        json["fieldVisitRegistrationId"].asString() shouldBe registration.toString()
                        json["fieldVisitGeneration"].asLong() shouldBe 2L
                        mapper.readTree(received[1].second)["attendanceDate"].asString() shouldBe "2026-10-22"
                    } finally {
                        server.stop(0)
                    }
                }
            }
        }
    })
