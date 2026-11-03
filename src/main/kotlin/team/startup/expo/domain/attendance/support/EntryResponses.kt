package team.startup.expo.domain.attendance.support

import team.startup.expo.domain.attendance.entity.EntryStatus
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.global.client.user.RecordEntryResDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 입장 스캔 응답 조립. 유저 서비스가 참가자 정보를 준 첫 입장과, 이 서비스가 만드는 응답(복구·재생, 재입장)을 나눈다. */
object EntryResponses {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 유저 서비스가 기록하고 참가자 정보를 준 첫 입장. */
    fun first(
        entry: RecordEntryResDto,
        enteredAt: LocalDateTime,
    ) = ScanEntryResDto(
        id = entry.id,
        name = entry.name,
        phoneNumber = entry.phoneNumber,
        personalInformationStatus = entry.personalInformationStatus,
        participationType = entry.participationType,
        badge = EntryBadge.of(entry.participationType == TRAINEE, entry.occupation, entry.name, entry.school),
        entryStatus = EntryStatus.FIRST,
        firstEnteredTime = enteredAt.format(TIME),
        badgePrint = entry.badgePrint,
        badgePrintJobId = entry.badgePrintJobId,
    )

    /**
     * 유저 서비스가 참가자 정보를 주지 않은 첫 입장 응답(재생, 빠진 로그 채움, 결과를 모르던 기록의 복구). 입장 응답처럼 `FIRST`와
     * 최초 입장 시각을 돌려주지만, 재입장 응답처럼 이름 외 정보(개인정보 동의 상태, 명찰, 명찰 출력)는 비어 있다.
     */
    fun restored(
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        name: String,
        firstEnteredAt: LocalDateTime,
    ) = withoutUserInfo(reqDto, subjectId, name, EntryStatus.FIRST, firstEnteredAt)

    /** 퇴장한 뒤 다시 들어온 재입장(#148). 최초 입장 시각을 함께 준다. */
    fun reEntry(
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        name: String,
        firstEnteredAt: LocalDateTime,
    ) = withoutUserInfo(reqDto, subjectId, name, EntryStatus.RE_ENTRY, firstEnteredAt)

    private fun withoutUserInfo(
        reqDto: ScanEntryReqDto,
        subjectId: Long,
        name: String,
        entryStatus: EntryStatus,
        firstEnteredAt: LocalDateTime,
    ) = ScanEntryResDto(
        id = subjectId,
        name = name,
        // 전화번호로 찍은 경우에만 찍은 번호를 돌려준다
        phoneNumber = reqDto.phoneNumber?.takeIf { reqDto.participantId == null },
        personalInformationStatus = null,
        participationType = reqDto.authority.participationType,
        badge = null,
        entryStatus = entryStatus,
        firstEnteredTime = firstEnteredAt.format(TIME),
    )

    private const val TRAINEE = "TRAINEE"
}
