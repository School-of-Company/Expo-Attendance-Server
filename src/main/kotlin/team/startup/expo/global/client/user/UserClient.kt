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

    /** 일반 참가자가 이 박람회에 모두 있는지 확인한다. 하나라도 없으면 404다. */
    @PostMapping("/internal/standard-participants/names")
    fun getStandardParticipantNames(
        @RequestBody request: StandardParticipantNamesReqDto,
    ): List<StandardParticipantNameResDto>

    /** 연수자가 이 박람회에 모두 있는지 확인한다. 하나라도 없으면 404다. */
    @PostMapping("/internal/trainees/names")
    fun getTraineeNames(
        @RequestBody request: TraineeNamesReqDto,
    ): List<TraineeNameResDto>

    /** 참가자 ID와 code가 맞으면 204다. 없는 참가자, 다른 박람회, code 불일치는 구분 없이 404이고 아무것도 기록하지 않는다. */
    @PostMapping("/internal/standard-participants/verify")
    fun verifyStandardParticipant(
        @RequestBody request: VerifyStandardParticipantReqDto,
    )

    /** 참가자 ID와 code가 맞으면 연결된 연수자 ID를 돌려준다. 없는 참가자, 다른 박람회, code 불일치, 연결 없음은 구분 없이 404다. */
    @PostMapping("/internal/standard-participants/trainee")
    fun resolveTraineeByParticipant(
        @RequestBody request: ResolveTraineeByParticipantReqDto,
    ): ResolveTraineeByParticipantResDto
}
