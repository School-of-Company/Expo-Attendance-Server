package team.startup.expo.global.client.expo.projection

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.config.ExpoProjectionProperties
import team.startup.expo.support.IntegrationTestSupport
import java.time.Instant
import java.util.UUID

/** 박람회 프로젝션 반영 규칙(#240): version 순서, 중복, 전체 덮어쓰기, tombstone. */
class ExpoProjectionTests : IntegrationTestSupport() {
    @Autowired
    lateinit var updater: ExpoProjectionUpdater

    @Autowired
    lateinit var repository: ExpoProjectionRepository

    private val start = Instant.parse("2030-01-01T01:00:00Z")
    private val end = Instant.parse("2030-01-01T02:00:00Z")

    private fun state(
        expoId: String,
        version: Long,
        startedDay: String = "2030-01-01",
        deleting: Boolean = false,
        standardIds: List<Long> = listOf(1),
        trainingIds: List<Long> = listOf(5),
        sessionIds: List<Long> = listOf(7),
        eventId: String = UUID.randomUUID().toString(),
    ) = ExpoChangedEvent(
        eventId = eventId,
        version = version,
        expoId = expoId,
        deleted = false,
        expo = ExpoChangedEvent.ExpoState(startedDay, "2030-01-02", deleting),
        standardPrograms = standardIds.map { ExpoChangedEvent.StandardProgramState(it, "2030-01-01T09:00", "2030-01-01T10:00") },
        trainingPrograms = trainingIds.map { ExpoChangedEvent.TrainingProgramState(it) },
        preregisterSessions = sessionIds.map { ExpoChangedEvent.PreregisterSessionState(it, start, end) },
    )

    private fun tombstone(
        expoId: String,
        version: Long,
    ) = ExpoChangedEvent(UUID.randomUUID().toString(), version, expoId, deleted = true)

    private fun newExpo() = UUID.randomUUID().toString()

    @Test
    fun `전체 상태를 반영하면 이 서비스가 읽는 값을 프로젝션에서 읽는다`() {
        val expoId = newExpo()

        updater.apply(state(expoId, 10)) shouldBe true

        repository.period(expoId) shouldBe ExpoPeriodResDto("2030-01-01", "2030-01-02")
        repository.standardProgram(expoId, 1) shouldBe
            StandardProgramResDto(1, startedAt = "2030-01-01T09:00", endedAt = "2030-01-01T10:00")
        repository.hasTrainingProgram(expoId, 5) shouldBe true
        repository.preregisterSession(expoId, 7) shouldBe PreregisterSessionResDto(7, start, end)
        repository.standardProgram(expoId, 2) shouldBe null
        repository.hasTrainingProgram("other-expo", 5) shouldBe false
    }

    @Test
    fun `같은 version(중복 eventId)과 더 작은 version은 버린다`() {
        val expoId = newExpo()
        val first = state(expoId, 10, eventId = "dup")
        updater.apply(first) shouldBe true

        updater.apply(first) shouldBe false
        updater.apply(state(expoId, 10, startedDay = "2030-02-01")) shouldBe false
        updater.apply(state(expoId, 9, startedDay = "2030-03-01", standardIds = emptyList())) shouldBe false

        repository.version(expoId) shouldBe 10
        repository.period(expoId)?.startedDay shouldBe "2030-01-01"
        repository.standardProgram(expoId, 1)?.id shouldBe 1
    }

    @Test
    fun `더 큰 version은 프로그램과 회차를 통째로 바꾼다`() {
        val expoId = newExpo()
        updater.apply(state(expoId, 10, standardIds = listOf(1, 2), trainingIds = listOf(5, 6), sessionIds = listOf(7, 8)))

        updater.apply(
            state(expoId, 12, startedDay = "2030-01-02", standardIds = listOf(2), trainingIds = listOf(6), sessionIds = listOf(8)),
        ) shouldBe
            true

        repository.version(expoId) shouldBe 12
        repository.period(expoId)?.startedDay shouldBe "2030-01-02"
        repository.standardProgram(expoId, 1) shouldBe null
        repository.standardProgram(expoId, 2)?.id shouldBe 2
        repository.hasTrainingProgram(expoId, 5) shouldBe false
        repository.hasTrainingProgram(expoId, 6) shouldBe true
        repository.preregisterSession(expoId, 7) shouldBe null
        repository.preregisterSession(expoId, 8)?.id shouldBe 8
    }

    @Test
    fun `tombstone은 프로젝션을 지우고 version을 남겨 옛 메시지로 되살리지 않는다`() {
        val expoId = newExpo()
        updater.apply(state(expoId, 10))

        updater.apply(tombstone(expoId, 11)) shouldBe true
        updater.apply(state(expoId, 10)) shouldBe false

        repository.version(expoId) shouldBe 11
        repository.period(expoId) shouldBe null
        repository.standardProgram(expoId, 1) shouldBe null
        repository.hasTrainingProgram(expoId, 5) shouldBe false
        repository.preregisterSession(expoId, 7) shouldBe null
    }

    @Test
    fun `처음 받은 메시지가 tombstone이어도 version을 기억한다`() {
        val expoId = newExpo()

        updater.apply(tombstone(expoId, 5)) shouldBe true
        updater.apply(state(expoId, 4)) shouldBe false

        repository.period(expoId) shouldBe null
    }

    @Test
    fun `삭제 중인 박람회는 기간만 읽고 프로그램과 회차는 없음으로 본다`() {
        val expoId = newExpo()

        updater.apply(state(expoId, 10, deleting = true))

        // 박람회 서비스도 삭제 중에 기간은 답하고 프로그램·회차는 409로 답한다. 없음이면 동기 호출로 넘어가 같은 결과가 된다
        repository.period(expoId) shouldBe ExpoPeriodResDto("2030-01-01", "2030-01-02")
        repository.standardProgram(expoId, 1) shouldBe null
        repository.hasTrainingProgram(expoId, 5) shouldBe false
        repository.preregisterSession(expoId, 7) shouldBe null
    }

    @Test
    fun `순서가 뒤바뀌어 와도 가장 큰 version만 남는다`() {
        val expoId = newExpo()

        listOf(3L, 1L, 5L, 2L, 4L).forEach { updater.apply(state(expoId, it, startedDay = "2030-01-0$it")) }

        repository.version(expoId) shouldBe 5
        repository.period(expoId)?.startedDay shouldBe "2030-01-05"
    }

    @Test
    fun `플래그를 켠 reader는 반영된 프로젝션을 읽고 없으면 null이다`() {
        val expoId = newExpo()
        updater.apply(state(expoId, 1))
        val reader = ExpoProjectionReader(repository, ExpoProjectionProperties(enabled = true))

        reader.period(expoId) shouldBe ExpoPeriodResDto("2030-01-01", "2030-01-02")
        reader.trainingProgram(expoId, 5) shouldBe Unit
        reader.period(newExpo()) shouldBe null
    }
}
