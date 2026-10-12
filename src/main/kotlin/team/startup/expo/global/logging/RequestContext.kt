package team.startup.expo.global.logging

import org.slf4j.MDC

/**
 * 요청 한 건의 로그를 이어 보기 위한 MDC 키와 도우미. 값은 [RequestIdFilter]가 요청이 끝날 때 지운다.
 * 박람회·참가자 ID는 개인정보가 아닌 식별자만 담는다(전화번호·코드·이름은 절대 넣지 않는다).
 */
object RequestContext {
    const val REQUEST_ID = "requestId"
    const val EXPO_ID = "expoId"
    const val PARTICIPANT_ID = "participantId"

    /** 요청 처리가 끝날 때 지울 키 */
    val KEYS = listOf(REQUEST_ID, EXPO_ID, PARTICIPANT_ID)

    /** 스캔 요청의 박람회·참가자 ID를 이후 로그(거절 로그 등)에 붙이도록 MDC에 둔다. */
    fun bindScan(
        expoId: String?,
        participantId: Long?,
    ) {
        expoId?.let { MDC.put(EXPO_ID, it) }
        participantId?.let { MDC.put(PARTICIPANT_ID, it.toString()) }
    }

    fun requestId(): String? = MDC.get(REQUEST_ID)
}
