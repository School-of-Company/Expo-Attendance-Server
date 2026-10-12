package team.startup.expo.domain.attendance.service.impl

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.entity.ProgramType
import team.startup.expo.domain.attendance.presentation.dto.request.ScanTrainingProgramReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.application.ProgramApplicationResDto
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.TrainingProgramBatchReqDto
import team.startup.expo.global.client.user.ResolveTraineeByParticipantReqDto
import team.startup.expo.global.client.user.ResolveTraineeByParticipantResDto
import team.startup.expo.global.client.user.TraineeNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.config.ExpoPeriodCacheProperties
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScanTrainingProgramServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val userClient = mockk<UserClient>()
    private val expoClient = mockk<ExpoClient>()
    private val applicationClient = mockk<ApplicationClient>()
    private val recordService = mockk<RecordProgramAttendanceService>()
    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)
    private val clock = UnitTestSupport.fixedClock(Instant.parse("2026-05-10T03:07:42Z"))

    private val service =
        ScanTrainingProgramServiceImpl(
            userClient,
            expoClient,
            applicationClient,
            UnitTestSupport.circuitBreaker("user"),
            UnitTestSupport.circuitBreaker("expo"),
            UnitTestSupport.circuitBreaker("application"),
            ExpoPeriodValidator(expoClient, UnitTestSupport.circuitBreaker("period"), ExpoPeriodCacheProperties(0), clock),
            recordService,
            CancellationGuardGate(UnitTestSupport.transactionManager(), guardRepository),
            clock,
        )

    private val expoId = "expo-1"
    private val programId = 9L
    private val traineeId = 31L
    private val linkedTraineeId = 32L
    private val byTrainee = ScanTrainingProgramReqDto(expoId, traineeId = traineeId)
    private val byParticipant = ScanTrainingProgramReqDto(expoId, participantId = 55L, code = "code-abc")

    private fun givenHappyPath(result: ProgramAttendanceResult = ProgramAttendanceResult.ENTERED) {
        every { userClient.getTraineeNames(any()) } returns emptyList()
        every { userClient.resolveTraineeByParticipant(any()) } returns ResolveTraineeByParticipantResDto(linkedTraineeId)
        every { expoClient.getTrainingPrograms(expoId, any()) } returns emptyList()
        every { expoClient.getPeriod(expoId) } returns ExpoPeriodResDto("2026-05-10", "2026-05-12")
        every { guardRepository.findByExpoIdAndParticipantId(any(), any()) } returns null
        every { guardRepository.findByExpoIdAndTraineeId(any(), any()) } returns null
        every { applicationClient.checkTrainingApplication(eq(programId), any()) } returns ProgramApplicationResDto(true)
        every { recordService.record(any(), any(), any(), any(), any()) } returns result
    }

    private fun guard(state: CancellationGuardState) =
        CancellationGuard(
            expoId = expoId,
            participantId = 55L,
            traineeId = linkedTraineeId,
            operationId = "op",
            state = state,
            createdAt = LocalDateTime.of(2026, 5, 10, 3, 0),
            updatedAt = LocalDateTime.of(2026, 5, 10, 3, 0),
        )

    init {
        given("연수 프로그램 스캔의 연수자 확인에서") {
            `when`("participantId만 보내면") {
                givenHappyPath()

                then("400을 던지고 유저 서비스를 부르지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "참가자 ID와 코드를 함께 보내야 합니다.") {
                        service.scan(programId, ScanTrainingProgramReqDto(expoId, participantId = 55L))
                    }
                    verify(exactly = 0) { userClient.resolveTraineeByParticipant(any()) }
                    verify(exactly = 0) { userClient.getTraineeNames(any()) }
                }
            }

            `when`("code만 보내면") {
                givenHappyPath()

                then("traineeId가 같이 와도 400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "참가자 ID와 코드를 함께 보내야 합니다.") {
                        service.scan(programId, ScanTrainingProgramReqDto(expoId, traineeId = traineeId, code = "code-abc"))
                    }
                }
            }

            `when`("participantId가 있고 code가 공백이면") {
                givenHappyPath()

                then("짝이 맞지 않아 400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "참가자 ID와 코드를 함께 보내야 합니다.") {
                        service.scan(programId, ScanTrainingProgramReqDto(expoId, participantId = 55L, code = " "))
                    }
                }
            }

            `when`("traineeId도 participantId와 code도 없으면") {
                givenHappyPath()

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "연수자 ID 또는 참가자 ID와 코드가 필요합니다.") {
                        service.scan(programId, ScanTrainingProgramReqDto(expoId))
                    }
                }
            }

            `when`("participantId와 code를 보내면") {
                givenHappyPath()
                service.scan(programId, byParticipant)

                then("유저 서비스가 찾아 준 연수자로 신청을 확인하고 기록한다") {
                    verify(
                        exactly = 1,
                    ) { userClient.resolveTraineeByParticipant(ResolveTraineeByParticipantReqDto(expoId, 55L, "code-abc")) }
                    verify(exactly = 1) { applicationClient.checkTrainingApplication(programId, linkedTraineeId) }
                    verify(exactly = 1) {
                        recordService.record(
                            ProgramType.TRAINING,
                            programId,
                            linkedTraineeId,
                            LocalDate.of(2026, 5, 10),
                            LocalTime.of(3, 7),
                        )
                    }
                }
                then("참가자 키와 연수자 키의 공유 락을 모두 잡는다") {
                    verify { guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, 55L)) }
                    verify { guardRepository.lockShared(CancellationGuardGate.traineeKey(expoId, linkedTraineeId)) }
                }
            }

            `when`("participantId와 code, traineeId를 모두 보내면") {
                givenHappyPath()
                service.scan(programId, ScanTrainingProgramReqDto(expoId, traineeId = traineeId, participantId = 55L, code = "code-abc"))

                then("참가자와 code가 우선하고 traineeId로는 조회하지 않는다") {
                    verify(exactly = 1) { applicationClient.checkTrainingApplication(programId, linkedTraineeId) }
                    verify(exactly = 0) { applicationClient.checkTrainingApplication(programId, traineeId) }
                    verify(exactly = 0) { userClient.getTraineeNames(any()) }
                }
            }

            `when`("참가자 확인에서 유저 서비스가 404로 답하면(틀린 code, 연결 없음)") {
                givenHappyPath()
                every { userClient.resolveTraineeByParticipant(any()) } throws UnitTestSupport.feignNotFound()

                then("404를 던지고 이후 확인을 하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.") { service.scan(programId, byParticipant) }
                    verify(exactly = 0) { expoClient.getTrainingPrograms(any(), any()) }
                }
            }

            `when`("참가자 확인에서 유저 서비스가 5xx로 답하면") {
                givenHappyPath()
                every { userClient.resolveTraineeByParticipant(any()) } throws UnitTestSupport.feignServerError()

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.") { service.scan(programId, byParticipant) }
                }
            }

            `when`("traineeId만 보내면") {
                givenHappyPath()
                service.scan(programId, byTrainee)

                then("연수자가 이 박람회에 있는지 확인하고 그 ID로 기록한다") {
                    verify(exactly = 1) { userClient.getTraineeNames(TraineeNamesReqDto(expoId, listOf(traineeId))) }
                    verify(exactly = 0) { userClient.resolveTraineeByParticipant(any()) }
                    verify(exactly = 1) { recordService.record(ProgramType.TRAINING, programId, traineeId, any(), any()) }
                }
                then("연수자 키의 공유 락만 잡는다") {
                    verify { guardRepository.lockShared(CancellationGuardGate.traineeKey(expoId, traineeId)) }
                    verify(exactly = 0) { guardRepository.lockShared(match { it.contains(":p:") }) }
                }
            }

            `when`("traineeId 확인에서 유저 서비스가 404로 답하면") {
                givenHappyPath()
                every { userClient.getTraineeNames(any()) } throws UnitTestSupport.feignNotFound()

                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.") { service.scan(programId, byTrainee) }
                }
            }
        }

        given("연수 프로그램 스캔의 프로그램과 기간 확인에서") {
            `when`("박람회 서비스가 프로그램을 404로 답하면") {
                givenHappyPath()
                every { expoClient.getTrainingPrograms(expoId, TrainingProgramBatchReqDto(listOf(programId))) } throws
                    UnitTestSupport.feignNotFound()

                then("404를 던지고 기록하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.") { service.scan(programId, byTrainee) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("박람회가 진행 중이 아니면") {
                givenHappyPath()
                every { expoClient.getPeriod(expoId) } returns ExpoPeriodResDto("2026-04-01", "2026-04-02")

                then("그 에러를 던지고 신청 확인과 기록을 하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") { service.scan(programId, byTrainee) }
                    verify(exactly = 0) { applicationClient.checkTrainingApplication(any(), any()) }
                }
            }
        }

        given("연수 프로그램 스캔의 신청 확인과 기록에서") {
            `when`("신청하지 않은 연수자이면") {
                givenHappyPath()
                every { applicationClient.checkTrainingApplication(programId, traineeId) } returns ProgramApplicationResDto(false)

                then("404를 던지고 기록하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수 프로그램을 참가 중인 유저를 찾지 못 했습니다.") { service.scan(programId, byTrainee) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("이미 입실한 연수자이면") {
                givenHappyPath(ProgramAttendanceResult.ALREADY_ENTERED)

                then("에러 없이 성공한다") {
                    shouldNotThrowAny { service.scan(programId, byTrainee) }
                }
            }

            `when`("기록 도중 프로그램이 삭제되었으면") {
                givenHappyPath(ProgramAttendanceResult.PROGRAM_DELETED)

                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수 프로그램을 찾지 못했습니다.") { service.scan(programId, byTrainee) }
                }
            }

            `when`("연결된 연수자 키에 신청 취소가 처리 중(HELD)이면") {
                givenHappyPath()
                every { guardRepository.findByExpoIdAndTraineeId(expoId, linkedTraineeId) } returns guard(CancellationGuardState.HELD)

                then("409로 거절하고 기록하지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.") { service.scan(programId, byParticipant) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("참가자 키에 신청 취소가 확정(COMMITTED)되었으면") {
                givenHappyPath()
                every { guardRepository.findByExpoIdAndParticipantId(expoId, 55L) } returns guard(CancellationGuardState.COMMITTED)

                then("400으로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { service.scan(programId, byParticipant) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }
        }
    }
}
