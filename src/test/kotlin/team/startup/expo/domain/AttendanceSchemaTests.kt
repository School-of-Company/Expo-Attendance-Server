package team.startup.expo.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import team.startup.expo.domain.attendance.entity.EntryOutbox
import team.startup.expo.domain.attendance.entity.StandardProgramAttendance
import team.startup.expo.domain.attendance.entity.TrainingProgramAttendance
import team.startup.expo.domain.attendance.repository.EntryOutboxRepository
import team.startup.expo.domain.attendance.repository.StandardProgramAttendanceRepository
import team.startup.expo.domain.attendance.repository.TrainingProgramAttendanceRepository
import team.startup.expo.domain.qr.entity.QrToken
import team.startup.expo.domain.qr.repository.QrEntryRepository
import team.startup.expo.domain.qr.repository.QrTokenRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class AttendanceSchemaTests : IntegrationTestSupport() {
    @Autowired
    lateinit var standardAttendanceRepository: StandardProgramAttendanceRepository

    @Autowired
    lateinit var trainingAttendanceRepository: TrainingProgramAttendanceRepository

    @Autowired
    lateinit var outboxRepository: EntryOutboxRepository

    @Autowired
    lateinit var qrTokenRepository: QrTokenRepository

    @Autowired
    lateinit var qrEntryRepository: QrEntryRepository

    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun `같은 참가자가 같은 일반 프로그램에 두 번 저장되면 유니크 위반이다`() {
        standardAttendanceRepository.saveAndFlush(standardAttendance(participantId = 1, programId = 10))

        assertThrows<DataIntegrityViolationException> {
            standardAttendanceRepository.saveAndFlush(standardAttendance(participantId = 1, programId = 10))
        }
    }

    @Test
    fun `같은 연수자가 같은 연수 프로그램에 두 번 저장되면 유니크 위반이다`() {
        trainingAttendanceRepository.saveAndFlush(trainingAttendance(traineeId = 1, programId = 10))

        assertThrows<DataIntegrityViolationException> {
            trainingAttendanceRepository.saveAndFlush(trainingAttendance(traineeId = 1, programId = 10))
        }
    }

    @Test
    fun `같은 날 같은 참가자의 아웃박스 행은 하나만 저장된다`() {
        outboxRepository.saveAndFlush(outbox(expoId = "expo-a", participantId = 1))

        assertThrows<DataIntegrityViolationException> {
            outboxRepository.saveAndFlush(outbox(expoId = "expo-a", participantId = 1))
        }
        outboxRepository.saveAndFlush(outbox(expoId = "expo-a", participantId = 2))
    }

    @Test
    fun `종이 QR은 같은 날 한 번만 입장하고 다음 날은 다시 입장할 수 있다`() {
        qrTokenRepository.save(QrToken(token = "token-daily", expoId = "expo-a", category = "ADULT"))
        val now = LocalDateTime.now()

        qrEntryRepository.insertIfAbsent("token-daily", "expo-a", today, now) shouldBe 1
        qrEntryRepository.insertIfAbsent("token-daily", "expo-a", today, now) shouldBe 0
        qrEntryRepository.insertIfAbsent("token-daily", "expo-a", today.plusDays(1), now) shouldBe 1
    }

    @Test
    fun `없는 토큰이나 다른 박람회 토큰은 입장이 기록되지 않는다`() {
        qrTokenRepository.save(QrToken(token = "token-other", expoId = "expo-a", category = "ADULT"))
        val now = LocalDateTime.now()

        qrEntryRepository.insertIfAbsent("unknown", "expo-a", today, now) shouldBe 0
        qrEntryRepository.insertIfAbsent("token-other", "expo-b", today, now) shouldBe 0
    }

    @Test
    fun `같은 토큰을 동시에 스캔해도 입장은 한 번만 기록된다`() {
        qrTokenRepository.save(QrToken(token = "token-race", expoId = "expo-a", category = "ADULT"))
        val threads = 8
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)

        val results =
            (1..threads)
                .map {
                    executor.submit(
                        Callable {
                            ready.countDown()
                            start.await()
                            qrEntryRepository.insertIfAbsent("token-race", "expo-a", today, LocalDateTime.now())
                        },
                    )
                }.let {
                    ready.await()
                    start.countDown()
                    it.map { future -> future.get() }
                }
        executor.shutdown()

        results.sum() shouldBe 1
    }

    private fun standardAttendance(
        participantId: Long,
        programId: Long,
    ) = StandardProgramAttendance(
        standardProgramId = programId,
        participantId = participantId,
        attendanceDate = today,
        entryTime = LocalTime.of(10, 0),
    )

    private fun trainingAttendance(
        traineeId: Long,
        programId: Long,
    ) = TrainingProgramAttendance(
        trainingProgramId = programId,
        traineeId = traineeId,
        attendanceDate = today,
        entryTime = LocalTime.of(10, 0),
    )

    private fun outbox(
        expoId: String,
        participantId: Long,
    ) = EntryOutbox(
        expoId = expoId,
        participantId = participantId,
        phoneNumber = "01012345678",
        attendanceDate = today,
    )
}
