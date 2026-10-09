package team.startup.expo.domain.attendance.service

import org.springframework.stereotype.Component
import team.startup.expo.domain.attendance.presentation.dto.request.ScanEntryReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.ScanEntryResDto
import team.startup.expo.global.config.EntryRetryProperties
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 방금 성공한 입구 스캔의 응답을 짧게 기억한다. 응답이 유실돼 클라이언트가 같은 QR을 다시 찍으면 유저 서비스는 이미 입장했다고
 * 답해 명찰 정보를 다시 받을 수 없으므로, 같은 스캐너 사용자가 같은 QR을 `windowSeconds` 안에 다시 찍으면 처음 응답을 돌려준다.
 *
 * 키에 스캐너 사용자(`X-User-Id`)를 넣어, 다른 직원이나 다른 입구에서 같은 QR로 다시 입장하는 것은 계속 거부한다. 응답에는
 * 이름과 소속, 명찰 QR 값이 들어 있으므로 서버 메모리에만 두고 시간이 지나면 지운다. 서버가 여러 대이면 다른 서버에 걸린
 * 재시도는 기억이 없어 "이미 입장"으로 폴백한다. 스캐너 사용자를 알 수 없으면 기억하지도 돌려주지도 않는다.
 */
@Component
class RecentEntryResponses(
    private val properties: EntryRetryProperties,
    private val clock: Clock,
) {
    private class Remembered(
        val response: ScanEntryResDto,
        val expiresAt: Instant,
    )

    private val entries = ConcurrentHashMap<String, Remembered>()

    fun find(
        expoId: String,
        reqDto: ScanEntryReqDto,
        scannerId: String?,
    ): ScanEntryResDto? {
        val key = key(expoId, reqDto, scannerId) ?: return null
        val remembered = entries[key] ?: return null
        if (!remembered.expiresAt.isAfter(clock.instant())) {
            entries.remove(key, remembered)
            return null
        }
        return remembered.response
    }

    fun remember(
        expoId: String,
        reqDto: ScanEntryReqDto,
        scannerId: String?,
        response: ScanEntryResDto,
    ) {
        val key = key(expoId, reqDto, scannerId) ?: return
        if (entries.size >= MAX_ENTRIES) purgeExpired()
        if (entries.size >= MAX_ENTRIES) return
        entries[key] = Remembered(response, clock.instant().plusSeconds(properties.windowSeconds))
    }

    private fun purgeExpired() {
        val now = clock.instant()
        entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
    }

    /** 같은 박람회·권한·QR 값(참가자 ID와 코드, 또는 전화번호)·스캐너 사용자가 같을 때만 같은 재시도로 본다. */
    private fun key(
        expoId: String,
        reqDto: ScanEntryReqDto,
        scannerId: String?,
    ): String? {
        if (properties.windowSeconds <= 0 || scannerId.isNullOrBlank()) return null
        val credential = reqDto.participantId?.let { "$it:${reqDto.code}" } ?: reqDto.phoneNumber ?: return null
        return listOf(expoId, reqDto.authority.name, credential, scannerId).joinToString("|")
    }

    private companion object {
        // 하루 입장 규모보다 충분히 크고 메모리는 작게 유지한다
        const val MAX_ENTRIES = 20_000
    }
}
