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

    /**
     * 관리자 취소용으로, 소유 확인 없이 참가자의 입장 여부와 연결된 연수자를 읽는다(제안, Expo-User-Server#117). 권한 검증은 호출하는 신청
     * 서비스가 관리자 쓰기 권한으로 이미 했다는 전제의 읽기 전용 조회다. 없는 참가자와 다른 박람회는 구분 없이 404다.
     */
    @PostMapping("/internal/standard-participants/admin-cancellation-status")
    fun getAdminCancellationStatus(
        @RequestBody request: AdminCancellationStatusReqDto,
    ): AdminCancellationStatusResDto

    /**
     * 신청 취소를 판단할 참가자의 소유·입장·연수 연결 상태를 읽는다(User 취소 권한 계약, Expo-User-Server#97). 박람회 전체의 입장
     * 기록 유무를 돌려주고 아무것도 만들거나 잠그지 않는다. 없는 참가자, 다른 박람회, 다른 대표자의 참가자, 미등록 번호는 구분 없이 404다.
     */
    @PostMapping("/internal/standard-participants/cancellation-status")
    fun getCancellationStatus(
        @RequestBody request: CancellationStatusReqDto,
    ): CancellationStatusResDto

    /**
     * 대표자의 일행(대표자 본인 포함)인 참가자의 QR `code`를 돌려준다(제안, Expo-User-Server#95). 없는 참가자, 다른 박람회,
     * 다른 대표자의 일행은 구분 없이 404이고 아무것도 기록하지 않는다.
     */
    @PostMapping("/internal/standard-participants/qr")
    fun getParticipantQr(
        @RequestBody request: ParticipantQrReqDto,
    ): ParticipantQrResDto
}
