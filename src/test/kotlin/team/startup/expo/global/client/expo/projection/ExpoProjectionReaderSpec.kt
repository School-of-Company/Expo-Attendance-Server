package team.startup.expo.global.client.expo.projection

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.config.ExpoProjectionProperties
import team.startup.expo.support.LogCapture
import java.time.Instant

class ExpoProjectionReaderSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val repository = mockk<ExpoProjectionRepository>()
    private val expoId = "expo-1"

    private fun reader(enabled: Boolean) = ExpoProjectionReader(repository, ExpoProjectionProperties(enabled = enabled))

    init {
        given("박람회 프로젝션 조회에서") {
            `when`("플래그가 꺼져 있으면") {
                val reader = reader(enabled = false)

                then("DB를 보지 않고 모두 null이라 지금처럼 박람회 서비스를 부른다") {
                    reader.period(expoId) shouldBe null
                    reader.standardProgram(expoId, 1) shouldBe null
                    reader.trainingProgram(expoId, 1) shouldBe null
                    reader.preregisterSession(expoId, 1) shouldBe null
                    verify(exactly = 0) { repository.period(any()) }
                    verify(exactly = 0) { repository.standardProgram(any(), any()) }
                    verify(exactly = 0) { repository.hasTrainingProgram(any(), any()) }
                    verify(exactly = 0) { repository.preregisterSession(any(), any()) }
                }
            }

            `when`("플래그가 켜져 있고 프로젝션에 있으면") {
                val session = PreregisterSessionResDto(3, Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T02:00:00Z"))
                every { repository.period(expoId) } returns ExpoPeriodResDto("2030-01-01", "2030-01-02")
                every { repository.standardProgram(expoId, 1) } returns StandardProgramResDto(1, startedAt = "2030-01-01T09:00")
                every { repository.hasTrainingProgram(expoId, 2) } returns true
                every { repository.preregisterSession(expoId, 3) } returns session
                val reader = reader(enabled = true)

                then("프로젝션 값을 돌려준다") {
                    reader.period(expoId) shouldBe ExpoPeriodResDto("2030-01-01", "2030-01-02")
                    reader.standardProgram(expoId, 1) shouldBe StandardProgramResDto(1, startedAt = "2030-01-01T09:00")
                    reader.trainingProgram(expoId, 2) shouldBe Unit
                    reader.preregisterSession(expoId, 3) shouldBe session
                }
            }

            `when`("플래그가 켜져 있고 프로젝션에 없으면") {
                every { repository.period(any()) } returns null
                every { repository.hasTrainingProgram(any(), any()) } returns false
                val reader = reader(enabled = true)

                then("null을 돌려주고 박람회·조회 종류마다 한 번만 로그를 남긴다") {
                    LogCapture(ExpoProjectionReader::class.java.name).use { logs ->
                        reader.period(expoId) shouldBe null
                        reader.period(expoId) shouldBe null
                        reader.trainingProgram(expoId, 2) shouldBe null
                        reader.period("expo-2") shouldBe null

                        logs.messages shouldHaveSize 3
                        logs.messages.first() shouldBe "Expo projection miss, falling back to Expo call: kind=period, expoId=expo-1"
                    }
                }
            }
        }
    }
}
