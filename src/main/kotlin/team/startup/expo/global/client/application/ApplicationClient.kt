package team.startup.expo.global.client.application

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
    name = "expo-application-server",
    url = "\${clients.application.url:}",
    configuration = [ApplicationClientConfiguration::class],
)
interface ApplicationClient {
    /** 일반 프로그램 신청 여부. 신청하지 않았으면 `applied=false`다. */
    @GetMapping("/internal/program-applications/standard/{programId}/participants/{participantId}")
    fun checkStandardApplication(
        @PathVariable("programId") programId: Long,
        @PathVariable("participantId") participantId: Long,
    ): ProgramApplicationResDto

    /** 연수 프로그램 신청 여부. 신청하지 않았으면 `applied=false`다. */
    @GetMapping("/internal/program-applications/training/{programId}/trainees/{traineeId}")
    fun checkTrainingApplication(
        @PathVariable("programId") programId: Long,
        @PathVariable("traineeId") traineeId: Long,
    ): ProgramApplicationResDto
}

data class ProgramApplicationResDto(
    val applied: Boolean,
)
