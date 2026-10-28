package team.startup.expo.domain.attendance.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "하루 입장자 목록(등록 QR). 최초 입장 시각 최신순이고, 한 사람(QR)은 그날 한 행이다")
data class EntryListResDto(
    val expoId: String,
    @field:Schema(description = "조회한 날짜")
    val date: LocalDate,
    val page: Int,
    val size: Int,
    @field:Schema(description = "그날 등록 QR로 입장한 사람 수(페이지와 상관없음)")
    val totalElements: Long,
    val entries: List<EntryListItemResDto>,
) {
    override fun toString() = "EntryListResDto(expoId=$expoId, date=$date, page=$page, totalElements=$totalElements)"
}

/** 입장 스캔 응답과 같은 모양이다. 전화번호는 가려서 담는다. */
data class EntryListItemResDto(
    @field:Schema(description = "참가자(연수자) ID", example = "55")
    val id: Long,
    @field:Schema(description = "참가 유형. `STANDARD` 일반 참가자, `TRAINEE` 연수자", example = "STANDARD")
    val participationType: String,
    @field:Schema(description = "이름. 입장 뒤 유저 서비스에서 지워진 참가자면 `null`이다", example = "홍길동", nullable = true)
    val name: String?,
    @field:Schema(
        description = "가운데를 가린 전화번호. 번호가 없는 참가자(동행자)나 지워진 참가자면 `null`이다",
        example = "010-****-5678",
        nullable = true,
    )
    val phoneNumber: String?,
    @field:Schema(description = "개인정보 수집 동의 여부. 지워진 참가자면 `null`이다", example = "true", nullable = true)
    val personalInformationStatus: Boolean?,
    @field:Schema(description = "명찰 정보. 명찰 출력 대상(연수자 전원, 교사·예비교사인 일반 참가자)이 아니거나 지워진 참가자면 `null`이다", nullable = true)
    val badge: BadgeResDto?,
    @field:Schema(description = "그날 최초 입장 시각(`HH:mm`). 퇴장 뒤 재입장해도 처음 입장한 시각이다", example = "10:05")
    val firstEnteredTime: String,
    @field:Schema(description = "지금 안에 있는지. 퇴장했으면 `false`, 재입장하면 다시 `true`다", example = "true")
    val inside: Boolean,
) {
    override fun toString() = "EntryListItemResDto(id=$id, participationType=$participationType)"
}
