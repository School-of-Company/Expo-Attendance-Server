package team.startup.expo.global.client.expo

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
    name = "expo-expo-server",
    url = "\${clients.expo.url:}",
    configuration = [ExpoClientConfiguration::class],
)
interface ExpoClient {
    @GetMapping("/internal/expo/{expoId}")
    fun getPeriod(
        @PathVariable("expoId") expoId: String,
    ): ExpoPeriodResDto
}
