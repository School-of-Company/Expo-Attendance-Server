package team.startup.expo.global.client.user

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "expo-user-server",
    url = "\${clients.user.url:}",
    configuration = [UserClientConfiguration::class],
)
interface UserClient {
    /** 참가자를 찾아 오늘의 입장을 기록한다. 참가자가 없으면 404, 오늘 이미 입장했으면 409다. */
    @PostMapping("/internal/entries")
    fun recordEntry(
        @RequestBody request: RecordEntryReqDto,
    ): RecordEntryResDto
}
