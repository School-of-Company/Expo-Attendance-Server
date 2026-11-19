package team.startup.expo.domain.attendance.support

import org.springframework.http.HttpStatus
import team.startup.expo.domain.attendance.presentation.dto.request.EntryAuthority
import team.startup.expo.global.exception.ExpectedException

/** 입장 스캔의 여러 단계가 같은 문구로 던지는 예외. */
object EntryErrors {
    fun alreadyEntered() = ExpectedException(HttpStatus.BAD_REQUEST, "이미 박람회에 입장한 유저입니다.")

    fun participantNotFound(authority: EntryAuthority) =
        ExpectedException(
            HttpStatus.NOT_FOUND,
            if (authority == EntryAuthority.ROLE_TRAINEE) "연수자를 찾지 못 했습니다." else "행사 참가자를 찾지 못 했습니다.",
        )
}
