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
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.repository.CancellationGuardRepository
import team.startup.expo.domain.attendance.service.CancellationGuardGate
import team.startup.expo.domain.attendance.service.ExpoPeriodValidator
import team.startup.expo.domain.attendance.service.PreregisterSessionValidator
import team.startup.expo.domain.attendance.service.RecentEntryResponses
import team.startup.expo.global.client.user.RecordEntryReqDto
import team.startup.expo.global.client.user.RecordEntryResDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.global.client.user.VerifyStandardParticipantReqDto
import team.startup.expo.global.config.EntryRetryProperties
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.UnitTestSupport
import team.startup.expo.support.shouldFailWith
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime

class ScanEntryServiceImplSpec : BehaviorSpec() {
    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val userClient = mockk<UserClient>()
    private val expoPeriodValidator = mockk<ExpoPeriodValidator>()
    private val preregisterSessionValidator = mockk<PreregisterSessionValidator>()
    private val guardRepository = mockk<CancellationGuardRepository>(relaxed = true)
    private val clock = UnitTestSupport.fixedClock()
    private val today = LocalDate.now(clock)

    private val service =
        ScanEntryServiceImpl(
            userClient,
            UnitTestSupport.circuitBreaker("user"),
            expoPeriodValidator,
            preregisterSessionValidator,
            RecentEntryResponses(EntryRetryProperties(windowSeconds = 300), clock),
            CancellationGuardGate(UnitTestSupport.transactionManager(), guardRepository),
            JsonMapper.builder().build(),
            clock,
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
        every { expoPeriodValidator.checkInProgress(expoId, today) } just Runs
        every { preregisterSessionValidator.check(any(), any(), any()) } just Runs
        every { guardRepository.findByExpoIdAndParticipantId(any(), any()) } returns null
    }

    init {
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

                then("전화번호로 입장을 기록하고 사전등록 확인과 취소 보호 락은 건너뛴다") {
                    res.id shouldBe 55L
                    verify(exactly = 1) { userClient.recordEntry(RecordEntryReqDto(expoId, "STANDARD", phoneNumber = "010-1111-2222")) }
                    verify(exactly = 0) { preregisterSessionValidator.check(any(), any(), any()) }
                    verify(exactly = 0) { guardRepository.lockShared(any()) }
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

        given("연수자가 입장 스캔을 할 때") {
            `when`("유저 서비스가 404로 답하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignNotFound()

                then("404 연수자 메시지를 던진다") {
                    shouldFailWith(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.") { service.scan(expoId, traineeReq) }
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
            }
        }

        given("참가자 ID와 코드로 입장하는 일반 참가자가") {
            `when`("스캔하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry(occupation = "PRE_SERVICE_TEACHER", phone = null)

                val res = service.scan(expoId, codeReq)

                then("참가자 ID와 코드로 입장을 기록하고 취소 보호 락 안에서 처리한다") {
                    verify(exactly = 1) {
                        userClient.recordEntry(RecordEntryReqDto(expoId, "STANDARD", participantId = 55L, code = "code-abc"))
                    }
                    verify { guardRepository.lockShared(CancellationGuardGate.participantKey(expoId, 55L)) }
                }
                then("사전등록 회차 확인을 요청하고 명찰 QR에는 코드를 담는다") {
                    verify(exactly = 1) { preregisterSessionValidator.check(expoId, 55L, any()) }
                    res.badge?.qrCode shouldBe """{"participantId":55,"code":"code-abc"}"""
                }
            }

            `when`("회차 확인이 넘겨받은 코드 확인을 실행하면") {
                givenPeriodOk()
                val verifyCode = slot<() -> Unit>()
                every { preregisterSessionValidator.check(expoId, 55L, capture(verifyCode)) } just Runs
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
                every { preregisterSessionValidator.check(expoId, 55L, capture(verifyCode)) } just Runs
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
                    ExpectedException(HttpStatus.BAD_REQUEST, "취소된 신청의 QR입니다.")

                then("입장을 기록하지 않는다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "취소된 신청의 QR입니다.") { service.scan(expoId, codeReq) }
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

        given("같은 스캐너가 같은 QR을 다시 찍는 재시도에서") {
            `when`("처음 스캔이 성공한 뒤 같은 스캐너 사용자로 다시 스캔하면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry()

                val first = service.scan(expoId, phoneReq, scannerId = "staff-1")
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()
                val second = service.scan(expoId, phoneReq, scannerId = "staff-1")

                then("유저 서비스를 다시 부르지 않고 처음 응답을 돌려준다") {
                    second shouldBe first
                    verify(exactly = 1) { userClient.recordEntry(any()) }
                }
            }

            `when`("다른 스캐너 사용자가 같은 QR을 찍으면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry()
                service.scan(expoId, phoneReq, scannerId = "staff-1")
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()

                then("기억된 응답을 쓰지 않고 이미 입장으로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") {
                        service.scan(expoId, phoneReq, scannerId = "staff-2")
                    }
                }
            }

            `when`("스캐너 사용자를 알 수 없으면") {
                givenPeriodOk()
                every { userClient.recordEntry(any()) } returns entry()
                service.scan(expoId, phoneReq, scannerId = null)
                every { userClient.recordEntry(any()) } throws UnitTestSupport.feignConflict()

                then("응답을 기억하지 않아 다시 찍으면 이미 입장으로 거절한다") {
                    shouldFailWith(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.") {
                        service.scan(expoId, phoneReq, scannerId = null)
                    }
                }
            }
        }
    }
}
