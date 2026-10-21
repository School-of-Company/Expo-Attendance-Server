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
import team.startup.expo.domain.attendance.presentation.dto.request.ScanStandardProgramReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.ProgramAttendanceResult
import team.startup.expo.domain.attendance.service.RecordProgramAttendanceService
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.application.ProgramApplicationResDto
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.StandardProgramResDto
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.config.ExpoPeriodCacheProperties
import team.startup.expo.global.config.ProgramAttendanceProperties
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScanStandardProgramServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val userClient = mockk<UserClient>()
    private val expoClient = mockk<ExpoClient>()
    private val applicationClient = mockk<ApplicationClient>()
    private val recordService = mockk<RecordProgramAttendanceService>()
    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)

    // 03:07:42 UTC. 기록 시각은 분 단위로 잘린다
    private val clock = UnitTestSupport.fixedClock(Instant.parse("2026-05-10T03:07:42Z"))

    private fun service(requireCode: Boolean = false) =
        ScanStandardProgramServiceImpl(
            userClient,
            expoClient,
            applicationClient,
            UnitTestSupport.circuitBreaker("user"),
            UnitTestSupport.circuitBreaker("expo"),
            UnitTestSupport.circuitBreaker("application"),
            ExpoPeriodValidator(expoClient, UnitTestSupport.circuitBreaker("period"), ExpoPeriodCacheProperties(0), clock),
            recordService,
            CancellationGuardGate(UnitTestSupport.transactionManager(), guardRepository),
            ProgramAttendanceProperties(requireCode),
            clock,
        )

    private val expoId = "expo-1"
    private val programId = 9L
    private val participantId = 55L
    private val withCode = ScanStandardProgramReqDto(expoId, participantId, code = "code-abc")
    private val withoutCode = ScanStandardProgramReqDto(expoId, participantId)

    /** 코드 확인부터 기록까지 모두 성공하는 기본 상태로 맞춘다. 각 경우가 한 곳만 바꿔 쓴다. */
    private fun givenHappyPath(result: ProgramAttendanceResult = ProgramAttendanceResult.ENTERED) {
        every { userClient.verifyStandardParticipant(any()) } just Runs
        every { userClient.getStandardParticipantNames(any()) } returns emptyList()
        every { expoClient.getStandardProgram(expoId, programId) } returns StandardProgramResDto(programId, "프로그램")
        every { expoClient.getPeriod(expoId) } returns ExpoPeriodResDto("2026-05-10", "2026-05-12")
        every { guardRepository.findByExpoIdAndParticipantId(any(), any()) } returns null
        every { applicationClient.checkStandardApplication(programId, participantId) } returns ProgramApplicationResDto(true)
        every { recordService.record(any(), any(), any(), any(), any()) } returns result
    }

    init {
        given("일반 프로그램 스캔의 참가자 확인에서") {
            `when`("code가 있으면") {
                givenHappyPath()
                service().scan(programId, withCode)

                then("참가자 ID와 code를 유저 서비스에서 확인하고 이름 조회는 하지 않는다") {
                    verify(
                        exactly = 1,
                    ) { userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, participantId, "code-abc")) }
                    verify(exactly = 0) { userClient.getStandardParticipantNames(any()) }
                }
            }

            `when`("code가 있고 유저 서비스가 404로 답하면(틀린 code)") {
                givenHappyPath()
                every { userClient.verifyStandardParticipant(any()) } throws UnitTestSupport.feignNotFound()

                then("참가자를 찾지 못한 것과 같은 404이고 이후 확인을 하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { expoClient.getStandardProgram(any(), any()) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("code 확인에서 유저 서비스가 5xx로 답하면") {
                givenHappyPath()
                every { userClient.verifyStandardParticipant(any()) } throws UnitTestSupport.feignServerError()

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.") { service().scan(programId, withCode) }
                }
            }

            `when`("code가 없고 require-code가 꺼져 있으면") {
                givenHappyPath()
                service(requireCode = false).scan(programId, withoutCode)

                then("참가자 ID만으로 존재를 확인한다") {
                    verify(
                        exactly = 1,
                    ) { userClient.getStandardParticipantNames(StandardParticipantNamesReqDto(expoId, listOf(participantId))) }
                    verify(exactly = 0) { userClient.verifyStandardParticipant(any()) }
                }
            }

            `when`("code가 공백이고 require-code가 꺼져 있으면") {
                givenHappyPath()
                service(requireCode = false).scan(programId, ScanStandardProgramReqDto(expoId, participantId, code = "  "))

                then("code가 없는 것으로 보고 이름 조회로 확인한다") {
                    verify(exactly = 1) { userClient.getStandardParticipantNames(any()) }
                    verify(exactly = 0) { userClient.verifyStandardParticipant(any()) }
                }
            }

            `when`("code가 없고 require-code가 켜져 있으면") {
                givenHappyPath()

                then("400을 던지고 유저 서비스를 부르지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "참가자 코드가 필요합니다.") { service(requireCode = true).scan(programId, withoutCode) }
                    verify(exactly = 0) { userClient.getStandardParticipantNames(any()) }
                    verify(exactly = 0) { userClient.verifyStandardParticipant(any()) }
                }
            }

            `when`("code가 없고 이름 조회가 404이면") {
                givenHappyPath()
                every { userClient.getStandardParticipantNames(any()) } throws UnitTestSupport.feignNotFound()

                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") { service().scan(programId, withoutCode) }
                }
            }

            `when`("code가 있으면 require-code가 켜져 있어도") {
                givenHappyPath()

                then("통과한다") {
                    shouldNotThrowAny { service(requireCode = true).scan(programId, withCode) }
                }
            }
        }

        given("일반 프로그램 스캔의 프로그램과 기간 확인에서") {
            `when`("박람회 서비스가 프로그램을 404로 답하면") {
                givenHappyPath()
                every { expoClient.getStandardProgram(expoId, programId) } throws UnitTestSupport.feignNotFound()

                then("404를 던지고 기록하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("박람회가 진행 중이 아니면") {
                givenHappyPath()
                every { expoClient.getPeriod(expoId) } returns ExpoPeriodResDto("2026-04-01", "2026-04-02")

                then("그 에러를 던지고 신청 확인과 기록을 하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { applicationClient.checkStandardApplication(any(), any()) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("일반 프로그램 스캔의 신청 확인과 기록에서") {
            `when`("신청한 참가자이면") {
                givenHappyPath()
                service().scan(programId, withCode)

                then("분 단위로 자른 시각으로 입실을 기록한다") {
                    verify(exactly = 1) {
                        recordService.record(ProgramType.STANDARD, programId, participantId, LocalDate.of(2026, 5, 10), LocalTime.of(3, 7))
                    }
                }
                then("참가자 키의 공유 락 안에서 처리한다") {
                    verify { guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, participantId)) }
                }
            }

            `when`("신청하지 않은 참가자이면") {
                givenHappyPath()
                every { applicationClient.checkStandardApplication(programId, participantId) } returns ProgramApplicationResDto(false)

                then("404를 던지고 기록하지 않는다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "일반 프로그램을 참가 중인 유저를 찾지 못 했습니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("신청 서비스가 5xx로 답하면") {
                givenHappyPath()
                every { applicationClient.checkStandardApplication(any(), any()) } throws UnitTestSupport.feignServerError()

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "신청 서비스를 잠시 사용할 수 없습니다.") { service().scan(programId, withCode) }
                }
            }

            `when`("이미 입실한 참가자이면") {
                givenHappyPath(ProgramAttendanceResult.ALREADY_ENTERED)

                then("에러 없이 성공한다") {
                    shouldNotThrowAny { service().scan(programId, withCode) }
                }
            }

            `when`("퇴실을 기록한 참가자이면") {
                givenHappyPath(ProgramAttendanceResult.LEFT)

                then("에러 없이 성공한다") {
                    shouldNotThrowAny { service().scan(programId, withCode) }
                }
            }

            `when`("기록 도중 프로그램이 삭제되었으면") {
                givenHappyPath(ProgramAttendanceResult.PROGRAM_DELETED)

                then("404를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "일반 프로그램을 찾지 못 했습니다.") { service().scan(programId, withCode) }
                }
            }

            `when`("신청 취소가 처리 중(HELD)이면") {
                givenHappyPath()
                every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns guard(CancellationGuardState.HELD)

                then("409로 거절하고 신청 확인과 기록을 하지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { applicationClient.checkStandardApplication(any(), any()) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }

            `when`("신청이 취소 확정(COMMITTED)되었으면") {
                givenHappyPath()
                every { guardRepository.findByExpoIdAndParticipantId(expoId, participantId) } returns
                    guard(CancellationGuardState.COMMITTED)

                then("400으로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { service().scan(programId, withCode) }
                    verify(exactly = 0) { recordService.record(any(), any(), any(), any(), any()) }
                }
            }
        }
    }

    private fun guard(state: CancellationGuardState) =
        CancellationGuard(
            expoId = expoId,
            participantId = participantId,
            operationId = "op",
            state = state,
            createdAt = LocalDateTime.of(2026, 5, 10, 3, 0),
            updatedAt = LocalDateTime.of(2026, 5, 10, 3, 0),
        )
}
