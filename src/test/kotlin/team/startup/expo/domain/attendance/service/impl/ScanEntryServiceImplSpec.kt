package team.startup.expo.domain.attendance.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.entity.CancellationGuard
import team.startup.expo.domain.attendance.entity.CancellationGuardState
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntryStatus
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.repository.EntryLogRepository
import team.startup.expo.domain.attendance.repository.FieldVisit
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.EntryLogService
import team.startup.expo.domain.attendance.service.EntryReplayPolicy
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.FieldEntryFence
import team.startup.expo.domain.attendance.service.PreregisterSessionValidator
import team.startup.expo.global.client.user.ParticipantEntryStatusResDto
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.ResolveParticipantResDto
import team.startup.expo.global.client.user.ResolveStandardParticipantReqDto
import team.startup.expo.global.client.user.ResolveStandardParticipantResDto
import team.startup.expo.global.client.user.StandardParticipantNameResDto
import team.startup.expo.global.client.user.TraineeNameResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.config.EntryReplayProperties
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ScanEntryServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val userClient = mockk<UserClient>()
    private val expoPeriodValidator = mockk<ExpoPeriodValidator>()
    private val preregisterSessionValidator = mockk<PreregisterSessionValidator>()
    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)
    private val entryLogService = mockk<EntryLogService>(relaxed = true)
    private val entryLogRepository = mockk<EntryLogRepository>(relaxed = true)
    private val clock = UnitTestSupport.fixedClock()
    private val fieldFence = mockk<FieldEntryFence>(relaxed = true)
    private val attemptId = UUID.randomUUID()
    private val today = LocalDate.now(clock)

    private val service =
        ScanEntryServiceImpl(
            userClient,
            UnitTestSupport.circuitBreaker("user"),
            expoPeriodValidator,
            preregisterSessionValidator,
            CancellationGuardGate(
                UnitTestSupport.transactionManager(),
                guardRepository,
                mockk(relaxed = true),
                UnitTestSupport.preregisterValidator(),
                clock,
            ),
            entryLogService,
            entryLogRepository,
            EntryReplayPolicy(EntryReplayProperties(windowSeconds = 30)),
            JsonMapper.builder().build(),
            clock,
            fieldFence,
        )

    private val expoId = "expo-1"
    private val phoneReq = ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, phoneNumber = "010-1111-2222")
    private val codeReq = ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, participantId = 55L, code = "code-abc")
    private val traineeReq = ScanEntryReqDto(EntryAuthority.ROLE_TRAINEE, phoneNumber = "010-3333-4444")

    private fun entry(
        id: Long = 55L,
        type: String = "STANDARD",
        occupation: String? = "STUDENT",
        phone: String? = "010-1111-2222",
    ) = RecordEntryResDto(id, "홍길동", phone, true, type, occupation, "한빛중")

    private fun givenPeriodOk() {
        every { userClient.getEntryStatus(any()) } answers { ParticipantEntryStatusResDto(55L, today, true) }
        every { expoPeriodValidator.checkInProgress(expoId, today) } just Runs
        every { preregisterSessionValidator.check(any(), any(), any()) } returns null
        every { preregisterSessionValidator.checkNotCancelled(any(), any(), any(), any()) } answers { thirdArg() }
        every { preregisterSessionValidator.activeField(any(), any(), any()) } returns null
        every { userClient.resolveStandardParticipant(any()) } returns ResolveStandardParticipantResDto(55L)
        every { guardRepository.findByExpoIdAndParticipantId(any(), any()) } returns null
    }

    init {
        every { fieldFence.begin(any(), any(), any()) } returns attemptId
        // 기본은 오늘 입장 로그가 없는 상태
        every { entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(any(), any(), any(), any()) } returns null

        given("FIELD 날짜별 권위 입장") {
            givenPeriodOk()
            val registration = UUID.randomUUID()
            every { preregisterSessionValidator.activeField(expoId, 55L, today) } returns
                FieldVisit(registration, expoId, 55L, today, 2, FieldVisitStatus.ACTIVE, UUID.randomUUID())

            `when`("첫입장이 성공하면") {
                every { userClient.recordEntry(any()) } returns entry()
                then("날짜·등록·세대를 전달하고 guard 안에서 durable fence를 확정한다") {
                    service.scan(expoId, codeReq)
                    io.mockk.verifyOrder {
                        guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, 55L))
                        fieldFence.begin(expoId, 55L, today)
                        userClient.recordEntry(
                            RecordEntryReqDto(
                                expoId,
                                "STANDARD",
                                participantId = 55L,
                                code = "code-abc",
                                attendanceDate = today,
                                fieldVisitRegistrationId = registration,
                                fieldVisitGeneration = 2,
                            ),
                        )
                        fieldFence.finish(attemptId, true)
                    }
                }
            }
            `when`("User 응답을 잃거나 5xx이면") {
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignServerError()
                then("취소가 late commit을 추월하지 못하게 PENDING을 남긴다") {
                    io.kotest.assertions.throwables
                        .shouldThrow<ExpectedException> { service.scan(expoId, codeReq) }
                        .status shouldBe
                        HttpStatus.SERVICE_UNAVAILABLE
                    verify { fieldFence.begin(expoId, 55L, today) }
                    verify(exactly = 0) { fieldFence.finish(any(), any()) }
                }
            }
            `when`("User가 확정적인 404이면") {
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignNotFound()
                then("이 실패한 시도의 fence만 해제한다") {
                    io.kotest.assertions.throwables
                        .shouldThrow<ExpectedException> { service.scan(expoId, codeReq) }
                        .status shouldBe
                        HttpStatus.NOT_FOUND
                    verify { fieldFence.finish(attemptId, false) }
                }
            }
            listOf("false", "wrong date", "wrong participant", "error").forEach { outcome ->
                `when`("User 409 뒤 entry-status가 $outcome 이면") {
                    every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()
                    then("저장되지 않은 stale scan을 입장으로 오인하지 않고 PENDING과 503을 유지한다") {
                        when (outcome) {
                            "false" -> {
                                every { userClient.getEntryStatus(any()) } returns ParticipantEntryStatusResDto(55, today, false)
                            }

                            "wrong date" -> {
                                every { userClient.getEntryStatus(any()) } returns
                                    ParticipantEntryStatusResDto(55, today.minusDays(1), true)
                            }

                            "wrong participant" -> {
                                every { userClient.getEntryStatus(any()) } returns
                                    ParticipantEntryStatusResDto(56, today, true)
                            }

                            else -> {
                                every { userClient.getEntryStatus(any()) } throws UnitTestSupport.feignServerError()
                            }
                        }
                        io.kotest.assertions.throwables
                            .shouldThrow<ExpectedException> { service.scan(expoId, codeReq) }
                            .status shouldBe
                            HttpStatus.SERVICE_UNAVAILABLE
                        verify(exactly = 0) { fieldFence.finish(any(), any()) }
                        verify(exactly = 0) { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) }
                    }
                }
            }
            `when`("User 409 뒤 entry-status가 오늘 첫입장을 확인하면 오늘 로그가 없어도") {
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()
                then("이미 기록된 첫입장은 취소 권위로 남기고 중복 입장 응답은 거절한다") {
                    io.kotest.assertions.throwables
                        .shouldThrow<ExpectedException> { service.scan(expoId, codeReq) }
                        .status shouldBe
                        HttpStatus.BAD_REQUEST
                    verify { fieldFence.finish(attemptId, true) }
                }
            }
        }

        given("PRE 전화번호의 날짜별 권위 입장") {
            givenPeriodOk()
            every { preregisterSessionValidator.checkNotCancelled(expoId, 55L, null, today) } returns 11L
            every { userClient.recordEntry(any()) } returns entry()
            `when`("오늘 earliest-start 회차를 선택했으면") {
                then("날짜와 회차를 보내고 FIELD tuple은 보내지 않는다") {
                    service.scan(expoId, phoneReq)
                    verify {
                        userClient.recordEntry(
                            RecordEntryReqDto(
                                expoId,
                                "STANDARD",
                                phoneNumber = phoneReq.phoneNumber,
                                preregisterSessionId = 11,
                                attendanceDate = today,
                            ),
                        )
                    }
                }
            }
        }

        given("409를 받고 오늘 입장 로그에 퇴장 기록이 있으면(재입장, #148)") {
            fun log(
                type: EntrySubjectType = EntrySubjectType.STANDARD,
                id: String = "55",
                exitedAt: LocalDateTime? = today.atTime(10, 0),
            ) = EntryLog(
                expoId = expoId,
                subjectType = type,
                subjectId = id,
                attendanceDate = today,
                enteredAt = today.atTime(9, 5),
                exitedAt = exitedAt,
            )
            val exitedLog = log()
            givenPeriodOk()
            every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()
            every { userClient.getStandardParticipantNames(any()) } returns listOf(StandardParticipantNameResDto(55L, "홍길동"))
            every { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) } returns 1

            `when`("일반 참가자가 전화번호로 스캔하면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.STANDARD,
                        "55",
                        today,
                    )
                } returns exitedLog

                then("RE_ENTRY와 최초 입장 시각을 응답하고 입장 로그를 다시 만들지 않는다") {
                    val res = service.scan(expoId, phoneReq)
                    res.entryStatus shouldBe EntryStatus.RE_ENTRY
                    res.firstEnteredTime shouldBe "09:05"
                    res.id shouldBe 55L
                    res.name shouldBe "홍길동"
                    res.badge shouldBe null
                    verify(exactly = 0) { entryLogService.recordEntry(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("퇴장 없이 찍었거나(퇴장 시각 없음) 입장 로그가 없으면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.STANDARD,
                        "55",
                        today,
                    )
                } returns log(exitedAt = null)

                then("400 이미 입장 메시지를 던지고 재입장을 기록하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, phoneReq) }
                    verify(exactly = 0) { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("동시에 다른 스캔이 먼저 재입장을 기록해 갱신 건수가 0이면") {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.STANDARD,
                        "55",
                        today,
                    )
                } returns exitedLog
                every { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) } returns 0

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, phoneReq) }
                }
            }

            `when`("연수자가 스캔하면") {
                every { userClient.resolveParticipant(any()) } returns ResolveParticipantResDto(77L, "TRAINEE")
                every { userClient.getTraineeNames(any()) } returns listOf(TraineeNameResDto(77L, "김연수"))
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.TRAINEE,
                        "77",
                        today,
                    )
                } returns log(EntrySubjectType.TRAINEE, "77")

                then("전화번호로 찾은 ID로 재입장하고 구분은 TRAINEE다") {
                    val res = service.scan(expoId, traineeReq)
                    res.entryStatus shouldBe EntryStatus.RE_ENTRY
                    res.id shouldBe 77L
                    res.participationType shouldBe "TRAINEE"
                }
            }
        }

        given("409를 받고 퇴장 없이 같은 스캐너가 재생 구간 안에 다시 찍으면(#176)") {
            fun enteredLog(
                enteredAt: LocalDateTime = LocalDateTime.now(clock).minusSeconds(10),
                exitedAt: LocalDateTime? = null,
                scanner: String? = "door-1",
            ) = EntryLog(
                expoId = expoId,
                subjectType = EntrySubjectType.STANDARD,
                subjectId = "55",
                attendanceDate = today,
                enteredAt = enteredAt,
                exitedAt = exitedAt,
                scanner = scanner,
            )

            fun givenLog(log: EntryLog) {
                every {
                    entryLogRepository.findByExpoIdAndSubjectTypeAndSubjectIdAndAttendanceDate(
                        expoId,
                        EntrySubjectType.STANDARD,
                        "55",
                        today,
                    )
                } returns log
            }
            givenPeriodOk()
            every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()
            every { userClient.getStandardParticipantNames(any()) } returns listOf(StandardParticipantNameResDto(55L, "홍길동"))
            every { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) } returns 1

            `when`("입장 후 10초 안에 같은 스캐너로 찍으면") {
                givenLog(enteredLog())

                then("FIRST와 최초 입장 시각을 응답하고 아무것도 기록하지 않는다") {
                    val res = service.scan(expoId, codeReq, "door-1")
                    res.entryStatus shouldBe EntryStatus.FIRST
                    res.firstEnteredTime shouldBe "02:59"
                    res.id shouldBe 55L
                    res.name shouldBe "홍길동"
                    verify(exactly = 0) { entryLogService.recordEntry(any(), any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("스캐너 이름이 한쪽에 없으면") {
                givenLog(enteredLog())

                then("같은 스캐너로 보고 재생한다") {
                    service.scan(expoId, codeReq, null).entryStatus shouldBe EntryStatus.FIRST
                }
            }

            `when`("재생 구간(30초)을 넘겨 찍으면") {
                givenLog(enteredLog(enteredAt = LocalDateTime.now(clock).minusSeconds(31)))

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, codeReq, "door-1") }
                }
            }

            `when`("다른 스캐너로 찍으면") {
                givenLog(enteredLog())

                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, codeReq, "door-2") }
                }
            }

            `when`("퇴장한 뒤 다시 찍으면") {
                givenLog(enteredLog(exitedAt = LocalDateTime.now(clock).minusSeconds(5)))

                then("재생이 아니라 재입장이다") {
                    service.scan(expoId, codeReq, "door-1").entryStatus shouldBe EntryStatus.RE_ENTRY
                    verify(exactly = 1) { entryLogRepository.reenter(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("입장 로그가 없으면") {
                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, codeReq, "door-1") }
                }
            }
        }

        given("신청이 취소된 참가자가 재생 구간 안에 다시 찍으면(#176)") {
            givenPeriodOk()
            every { preregisterSessionValidator.checkNotCancelled(any(), any(), any(), any()) } throws
                ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")

            `when`("스캔하면") {
                then("재생하지 않고 취소 오류를 그대로 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { service.scan(expoId, codeReq, "door-1") }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }
        }

        given("입장 스캔의 식별자 검증에서") {
            `when`("연수자가 전화번호 없이 스캔하면") {
                then("400을 던지고 유저 서비스를 부르지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "연수자는 전화번호가 필요합니다.") {
                        service.scan(expoId, ScanEntryReqDto(EntryAuthority.ROLE_TRAINEE, participantId = 1L, code = "c"))
                    }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }

            `when`("일반 참가자가 전화번호도 참가자 ID와 코드도 없이 스캔하면") {
                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "전화번호 또는 참가자 ID와 코드가 필요합니다.") {
                        service.scan(expoId, ScanEntryReqDto(EntryAuthority.ROLE_STANDARD))
                    }
                }
            }

            `when`("일반 참가자가 참가자 ID만 보내면") {
                then("전화번호가 있어도 짝이 맞지 않아 400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "전화번호 또는 참가자 ID와 코드가 필요합니다.") {
                        service.scan(expoId, ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, phoneNumber = "010", participantId = 55L))
                    }
                }
            }

            `when`("일반 참가자가 코드만 보내면") {
                then("400을 던진다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "전화번호 또는 참가자 ID와 코드가 필요합니다.") {
                        service.scan(expoId, ScanEntryReqDto(EntryAuthority.ROLE_STANDARD, code = "code-abc"))
                    }
                }
            }
        }

        given("전화번호로 입장하는 일반 참가자가") {
            `when`("진행 중인 박람회에서 스캔하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry()

                val res = service.scan(expoId, phoneReq)

                then("전화번호로 입장을 기록하고 회차 시간 확인 없이 취소 보호 락을 잡는다") {
                    res.id shouldBe 55L
                    verify(
                        exactly = 1,
                    ) {
                        userClient.recordEntry(
                            RecordEntryReqDto(expoId, "STANDARD", phoneNumber = "010-1111-2222", attendanceDate = today),
                        )
                    }
                    verify(exactly = 0) { preregisterSessionValidator.check(any(), any(), any()) }
                    verify(exactly = 1) { guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, 55L)) }
                }
                then("교사가 아니면 명찰이 없다") {
                    res.badge shouldBe null
                }
            }

            `when`("교사이면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry(occupation = "TEACHER")

                val res = service.scan(expoId, phoneReq)

                then("이름, 소속과 이전 형식의 명찰 QR을 돌려준다") {
                    res.badge?.name shouldBe "홍길동"
                    res.badge?.school shouldBe "한빛중"
                    res.badge?.qrCode shouldBe """{"participantId":55,"phoneNumber":"010-1111-2222"}"""
                }
            }

            `when`("박람회가 진행 중이 아니면") {
                every { expoPeriodValidator.checkInProgress(expoId, today) } throws
                    ExpectedException(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.")

                then("그 에러를 그대로 던지고 입장을 기록하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "해당 박람회는 진행 중인 상태가 아닙니다.") { service.scan(expoId, phoneReq) }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }

            `when`("유저 서비스가 404로 답하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignNotFound()

                then("404 행사 참가자 메시지를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") { service.scan(expoId, phoneReq) }
                }
            }

            `when`("유저 서비스가 오늘 이미 입장했다고(409) 답하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()

                then("400 이미 입장 메시지로 바꾼다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, phoneReq) }
                }
            }

            `when`("유저 서비스가 5xx로 답하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignServerError()

                then("503을 던진다") {
                    shouldFailWith(HttpStatus.SERVICE_UNAVAILABLE, "유저 서비스를 잠시 사용할 수 없습니다.") { service.scan(expoId, phoneReq) }
                }
            }
        }

        given("전화번호로 입장하는 참가자가 확정된 회차를 가졌을 때") {
            `when`("스캔하면") {
                givenPeriodOk()
                every { preregisterSessionValidator.checkNotCancelled(expoId, 55L, null, today) } returns null
                every { userClient.recordEntry(any()) } returns entry()

                service.scan(expoId, phoneReq)

                then("회차 시간을 확인하지 않았으므로 회차를 붙이지 않는다") {
                    verify(
                        exactly = 1,
                    ) {
                        userClient.recordEntry(
                            RecordEntryReqDto(expoId, "STANDARD", phoneNumber = "010-1111-2222", attendanceDate = today),
                        )
                    }
                }
            }
        }

        given("전화번호 입장의 사전 조회와 취소 보호에서") {
            listOf(404, 500).forEach { status ->
                `when`("참가자 조회가 $status 이면") {
                    givenPeriodOk()
                    every { userClient.resolveStandardParticipant(any()) } throws
                        if (status == 404) UnitTestSupport.feignNotFound() else UnitTestSupport.feignServerError()
                    then("입장을 기록하지 않고 조회 실패를 돌려준다") {
                        shouldFailWith(
                            if (status == 404) HttpStatus.NOT_FOUND else HttpStatus.SERVICE_UNAVAILABLE,
                            if (status == 404) "행사 참가자를 찾지 못 했습니다." else "유저 서비스를 잠시 사용할 수 없습니다.",
                        ) { service.scan(expoId, phoneReq) }
                        verify(exactly = 0) { userClient.recordEntry(any()) }
                    }
                }
            }
            CancellationGuardState.entries.forEach { state ->
                `when`("전화번호 참가자 guard가 $state 이면") {
                    givenPeriodOk()
                    every { guardRepository.findByExpoIdAndParticipantId(expoId, 55L) } returns
                        CancellationGuard(
                            expoId = expoId,
                            participantId = 55L,
                            operationId = "op",
                            state = state,
                            createdAt = LocalDateTime.now(clock),
                            updatedAt = LocalDateTime.now(clock),
                        )
                    then("QR과 같은 응답으로 거절하고 기록하지 않는다") {
                        shouldFailWith(
                            if (state == CancellationGuardState.HELD) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST,
                            if (state == CancellationGuardState.HELD) "신청 취소를 처리 중입니다." else "취소된 신청입니다.",
                        ) { service.scan(expoId, phoneReq) }
                        verify(exactly = 0) { userClient.recordEntry(any()) }
                    }
                }
            }
        }

        given("연수자가 입장 스캔을 할 때") {
            `when`("유저 서비스가 404로 답하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignNotFound()

                then("404 연수자 메시지를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.") { service.scan(expoId, traineeReq) }
                }
                then("입장이 실패했으므로 입장 로그를 남기지 않는다") {
                    verify(exactly = 0) { entryLogService.recordEntry(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("입장에 성공하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns
                    entry(id = 31L, type = "TRAINEE", occupation = null, phone = "010-3333-4444")

                val res = service.scan(expoId, traineeReq)

                then("TRAINEE로 기록하고 연수자 전원에게 traineeId 명찰 QR을 준다") {
                    verify { userClient.recordEntry(RecordEntryReqDto(expoId, "TRAINEE", phoneNumber = "010-3333-4444")) }
                    res.badge?.qrCode shouldBe """{"traineeId":31,"phoneNumber":"010-3333-4444"}"""
                }
                then("입장 로그를 TRAINEE와 연수자 ID로 남긴다") {
                    verify(exactly = 1) { entryLogService.recordEntry(expoId, EntrySubjectType.TRAINEE, "31") }
                }
            }
        }

        given("참가자 ID와 코드로 입장하는 일반 참가자가") {
            `when`("스캔하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry(occupation = "PRE_SERVICE_TEACHER", phone = null)

                val res = service.scan(expoId, codeReq)

                then("참가자 ID와 코드로 입장을 기록하고 취소 보호 락 안에서 처리한다") {
                    verify(exactly = 1) {
                        userClient.recordEntry(
                            RecordEntryReqDto(expoId, "STANDARD", participantId = 55L, code = "code-abc", attendanceDate = today),
                        )
                    }
                    verify { guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, 55L)) }
                }
                then("사전등록 회차 확인을 요청하고 명찰 QR에는 코드를 담는다") {
                    verify(exactly = 1) { preregisterSessionValidator.check(expoId, 55L, any()) }
                    res.badge?.qrCode shouldBe """{"participantId":55,"code":"code-abc"}"""
                }
                then("입장 로그를 STANDARD와 참가자 ID로 남기고 코드는 남기지 않는다") {
                    verify(
                        exactly = 1,
                    ) { entryLogService.recordEntry(expoId, EntrySubjectType.STANDARD, "55", enteredAt = LocalDateTime.now(clock)) }
                }
            }

            `when`("신청한 회차의 입장 시간에 스캔하면") {
                givenPeriodOk()
                every { preregisterSessionValidator.check(expoId, 55L, any()) } returns 11L
                every { preregisterSessionValidator.checkNotCancelled(expoId, 55L, 11L, today) } returns 11L
                every { userClient.recordEntry(any()) } returns entry()

                service.scan(expoId, codeReq)

                then("확인한 회차 ID를 preregisterSessionId로 보낸다") {
                    verify(exactly = 1) {
                        userClient.recordEntry(
                            RecordEntryReqDto(
                                expoId,
                                "STANDARD",
                                participantId = 55L,
                                code = "code-abc",
                                preregisterSessionId = 11L,
                                attendanceDate = today,
                            ),
                        )
                    }
                }
            }

            `when`("회차를 고른 뒤 락을 잡기 전에 그 회차가 취소되면") {
                givenPeriodOk()
                every { preregisterSessionValidator.check(expoId, 55L, any()) } returns 11L
                every { preregisterSessionValidator.checkNotCancelled(expoId, 55L, 11L, today) } throws
                    ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")

                then("입장을 기록하지 않고 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { service.scan(expoId, codeReq) }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }

            `when`("같은 날 같은 회차로 이미 입장해 유저 서비스가 409로 답하면") {
                givenPeriodOk()
                every { preregisterSessionValidator.check(expoId, 55L, any()) } returns 11L
                every { preregisterSessionValidator.checkNotCancelled(expoId, 55L, 11L, today) } returns 11L
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()

                then("기존과 같이 400 이미 입장 메시지로 바꾸고 입장 로그를 남기지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") { service.scan(expoId, codeReq) }
                    verify(exactly = 0) { entryLogService.recordEntry(any(), any(), any(), any(), any(), any()) }
                }
            }

            `when`("회차 확인이 넘겨받은 코드 확인을 실행하면") {
                givenPeriodOk()
                val verifyCode = slot<() -> Unit>()
                every { preregisterSessionValidator.check(expoId, 55L, capture(verifyCode)) } returns null
                every { userClient.recordEntry(any()) } returns entry()
                every { userClient.verifyStandardParticipant(any()) } just Runs

                service.scan(expoId, codeReq)
                verifyCode.captured.invoke()

                then("유저 서비스에 참가자 ID와 코드를 보내 확인한다") {
                    verify(exactly = 1) {
                        userClient.verifyStandardParticipant(VerifyStandardParticipantReqDto(expoId, 55L, "code-abc"))
                    }
                }
            }

            `when`("코드 확인에서 유저 서비스가 404로 답하면") {
                givenPeriodOk()
                val verifyCode = slot<() -> Unit>()
                every { preregisterSessionValidator.check(expoId, 55L, capture(verifyCode)) } returns null
                every { userClient.recordEntry(any()) } returns entry()
                every { userClient.verifyStandardParticipant(any()) } throws UnitTestSupport.feignNotFound()

                service.scan(expoId, codeReq)

                then("입장 기록과 같은 404 행사 참가자 메시지가 된다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.") { verifyCode.captured.invoke() }
                }
            }

            `when`("사전등록 회차 확인이 거절하면") {
                every { expoPeriodValidator.checkInProgress(expoId, today) } just Runs
                every { preregisterSessionValidator.check(any(), any(), any()) } throws
                    ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청입니다.")

                then("입장을 기록하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청입니다.") { service.scan(expoId, codeReq) }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }

            `when`("취소가 처리 중(HELD)이면") {
                givenPeriodOk()
                every { guardRepository.findByExpoIdAndParticipantId(expoId, 55L) } returns
                    CancellationGuard(
                        expoId = expoId,
                        participantId = 55L,
                        operationId = "op",
                        state = CancellationGuardState.HELD,
                        createdAt = LocalDateTime.now(clock),
                        updatedAt = LocalDateTime.now(clock),
                    )

                then("409로 거절하고 입장을 기록하지 않는다") {
                    shouldFailWith(HttpStatus.CONFLICT, "신청 취소를 처리 중입니다.") { service.scan(expoId, codeReq) }
                    verify(exactly = 0) { userClient.recordEntry(any()) }
                }
            }
        }
    }
}
