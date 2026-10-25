package team.startup.expo.domain.attendance.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.domain.attendance.entity.EntryLog
import team.startup.expo.domain.attendance.entity.EntrySubjectType
import team.startup.expo.global.config.EntryReplayProperties
import java.time.LocalDateTime

class EntryReplayPolicySpec :
    BehaviorSpec({
        val now = LocalDateTime.of(2026, 5, 10, 3, 0, 0)
        val policy = EntryReplayPolicy(EntryReplayProperties(windowSeconds = 30))

        fun log(
            enteredAt: LocalDateTime = now.minusSeconds(10),
            exitedAt: LocalDateTime? = null,
            scanner: String? = "door-1",
        ) = EntryLog(
            expoId = "expo-1",
            subjectType = EntrySubjectType.STANDARD,
            subjectId = "55",
            attendanceDate = now.toLocalDate(),
            enteredAt = enteredAt,
            exitedAt = exitedAt,
            scanner = scanner,
        )

        given("재생 판단") {
            then("창 안, 퇴장 없음, 같은 스캐너면 허용한다") { policy.allows(log(), now, "door-1") shouldBe true }
            then("정확히 창 경계(30초)는 허용한다") { policy.allows(log(enteredAt = now.minusSeconds(30)), now, "door-1") shouldBe true }
            then("창을 넘기면 거절한다") { policy.allows(log(enteredAt = now.minusSeconds(31)), now, "door-1") shouldBe false }
            then("스캐너가 다르면 거절한다") { policy.allows(log(), now, "door-2") shouldBe false }
            then("스캐너가 한쪽에 없으면 허용한다") {
                policy.allows(log(), now, null) shouldBe true
                policy.allows(log(scanner = null), now, "door-2") shouldBe true
            }
            then("퇴장했으면 거절한다") { policy.allows(log(exitedAt = now.minusSeconds(1)), now, "door-1") shouldBe false }
            then("로그가 없으면 거절한다") { policy.allows(null, now, "door-1") shouldBe false }
            then("입장 시각이 미래면 거절한다") { policy.allows(log(enteredAt = now.plusSeconds(5)), now, "door-1") shouldBe false }
            then("창이 0이면 끈다") {
                EntryReplayPolicy(EntryReplayProperties(windowSeconds = 0)).allows(log(), now, "door-1") shouldBe false
            }
        }
    })
