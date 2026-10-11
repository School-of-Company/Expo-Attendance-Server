package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.AcquireCancellationGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.CommitCancellationGuardReqDto

sealed interface AcquireCancellationGuardResult {
    /** 취소 보호를 걸었다. 이 참가자의 입장·프로그램 스캔은 commit이나 abort가 올 때까지 거절된다. */
    data object Acquired : AcquireCancellationGuardResult

    /** 이미 입장했거나 프로그램에 출석해 취소할 수 없다. 보호를 걸지 않았다. */
    data class Blocked(
        val entered: Boolean,
        val programAttended: Boolean,
    ) : AcquireCancellationGuardResult
}

/**
 * 신청 서비스가 참가자 신청을 취소하는 동안 입장·프로그램 스캔과 경합하지 않게 하는 취소 보호(#79). `operationId`로 멱등이고,
 * 시간이 지나도 자동으로 풀리지 않는다. 신청 서비스가 commit이나 abort를 보낼 때까지 유지한다.
 */
interface CancellationGuardService {
    /**
     * 진행 중인 스캔이 끝나기를 기다린 뒤 보호를 걸고, 입장 여부(유저 서비스)와 프로그램 출석(이 서비스)이 없을 때만 유지한다.
     * 같은 `operationId`로 다시 불러도 같은 결과이고, 다른 작업이 이미 보호를 걸었으면 409다.
     */
    fun acquire(
        expoId: String,
        participantId: Long,
        operationId: String,
        reqDto: AcquireCancellationGuardReqDto,
    ): AcquireCancellationGuardResult

    /**
     * 취소를 확정한다. 재신청으로 회차가 다시 기록될 때까지 스캔을 계속 막는다. 같은 `operationId`는 멱등이다.
     * `reqDto.revision`은 이 취소의 사전등록 회차 DELETE revision이다. 이미 그보다 새로운 회차 PUT이 적용되었다면 이 취소는 새 신청에
     * 밀린 것이라 보호를 확정하지 않고 풀며, 아니면 이 revision을 기억해 더 오래된 PUT이 보호를 풀지 못하게 한다(#92).
     */
    fun commit(
        expoId: String,
        participantId: Long,
        operationId: String,
        reqDto: CommitCancellationGuardReqDto?,
    )

    /** 취소를 중단하고 보호를 푼다. 없는 작업은 아무것도 하지 않는다. 이미 확정된 작업은 409다. */
    fun abort(
        expoId: String,
        participantId: Long,
        operationId: String,
    )

    /**
     * 재신청으로 회차가 다시 기록되면 확정된 취소 보호를 지운다. `revision`이 있으면 취소를 확정할 때 받은 revision보다 새로울 때만
     * 지운다. 어느 쪽이든 revision이 없으면 기존처럼 지운다.
     */
    fun releaseCommitted(
        expoId: String,
        participantId: Long,
        revision: Long?,
    )
}
