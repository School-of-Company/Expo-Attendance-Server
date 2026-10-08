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

    /** 박람회 ID와 전화번호로 참가자 ID를 찾는다. 없으면 404다. */
    @PostMapping("/internal/participants/resolve")
    fun resolveParticipant(
        @RequestBody request: ResolveParticipantReqDto,
    ): ResolveParticipantResDto

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

    /** 일반 참가자 요약(이름, 번호)을 조회한다. 없는 id나 다른 박람회의 참가자가 있으면 404다. */
    @PostMapping("/internal/standard-participants/details")
    fun getStandardParticipantBriefs(
        @RequestBody request: StandardParticipantBriefsReqDto,
    ): List<StandardParticipantBriefResDto>

    /** 참가자 ID와 code가 맞으면 204다. 없는 참가자, 다른 박람회, code 불일치는 구분 없이 404이고 아무것도 기록하지 않는다. */
    @PostMapping("/internal/standard-participants/verify")
    fun verifyStandardParticipant(
        @RequestBody request: VerifyStandardParticipantReqDto,
    )
}
