package team.startup.expo.domain.attendance.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.domain.attendance.presentation.dto.response.BadgeResDto

class EntryListFormatSpec :
    BehaviorSpec({
        given("전화번호 가리기") {
            then("하이픈 유무와 상관없이 앞 3자리와 끝 4자리만 남긴다") {
                PhoneNumberMask.mask("010-1234-5678") shouldBe "010-****-5678"
                PhoneNumberMask.mask("01012345678") shouldBe "010-****-5678"
                PhoneNumberMask.mask("0101234567") shouldBe "010-****-4567"
            }
            then("짧은 번호는 끝 4자리만, 4자리보다 짧으면 모두 가린다") {
                PhoneNumberMask.mask("12345") shouldBe "****-2345"
                PhoneNumberMask.mask("123") shouldBe "****"
            }
            then("번호가 없으면 null이다") {
                PhoneNumberMask.mask(null) shouldBe null
                PhoneNumberMask.mask(" ") shouldBe null
            }
        }

        given("명찰 규칙") {
            then("연수자는 직업과 상관없이 명찰을 받는다") {
                EntryBadge.of(true, null, "연수", "빛고을초") shouldBe BadgeResDto("연수", "빛고을초")
            }
            then("일반 참가자는 교사와 예비교사만 명찰을 받는다") {
                EntryBadge.of(false, "TEACHER", "교사", "빛고을고") shouldBe BadgeResDto("교사", "빛고을고")
                EntryBadge.of(false, "PRE_SERVICE_TEACHER", "예비", null) shouldBe BadgeResDto("예비", null)
                EntryBadge.of(false, "STUDENT", "학생", "빛고을고") shouldBe null
                EntryBadge.of(false, null, "모름", null) shouldBe null
            }
        }
    })
