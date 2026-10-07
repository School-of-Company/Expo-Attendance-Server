package team.startup.expo.domain.attendance.event

/**
 * 문자 서비스가 구독하는 입장 이벤트 페이로드. 일반 참가자만 발행하므로 `participationType`은 항상 `STANDARD`다.
 * `eventId`는 재전송에서도 같아서 소비자가 중복 수신을 걸러낼 수 있다.
 */
data class EntryRecordedEvent(
    val eventId: String,
    val expoId: String,
    val participationType: String = "STANDARD",
    val id: Long,
    val phoneNumber: String,
) {
    // 전화번호가 로그에 남지 않게 한다
    override fun toString() = "EntryRecordedEvent(eventId=$eventId, expoId=$expoId)"
}
