package team.startup.expo.global.client.expo

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

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

    /** 이 박람회의 일반 프로그램을 조회한다. 박람회나 프로그램이 없으면 404다. */
    @GetMapping("/internal/expo/{expoId}/standard-programs/{programId}")
    fun getStandardProgram(
        @PathVariable("expoId") expoId: String,
        @PathVariable("programId") programId: Long,
    ): StandardProgramResDto

    /** 이 박람회의 연수 프로그램을 조회한다. 없거나 다른 박람회의 프로그램이 하나라도 있으면 404다. */
    @PostMapping("/internal/expo/{expoId}/training-programs/batch")
    fun getTrainingPrograms(
        @PathVariable("expoId") expoId: String,
        @RequestBody request: TrainingProgramBatchReqDto,
    ): List<TrainingProgramResDto>

    /** 사전등록 회차 하나를 조회한다. 박람회나 회차가 없거나 다른 박람회의 회차면 404, 삭제 중인 박람회면 409다. */
    @GetMapping("/internal/expo/{expoId}/preregister-sessions/{sessionId}")
    fun getPreregisterSession(
        @PathVariable("expoId") expoId: String,
        @PathVariable("sessionId") sessionId: Long,
    ): PreregisterSessionResDto
}
