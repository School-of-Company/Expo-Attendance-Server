package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitCancellationResDto
import java.util.UUID

/**
 * 날짜별 현장 방문(FIELD) 등록의 projection과 그 날짜 취소 보호(#204, `docs/field-visits.md`). 등록 ID·참가자·방문일·generation은
 * 신청 서비스가 소유하고 이 서비스는 그대로 반영한다. 모든 작업은 박람회 락 → 참가자 배타 락 → 등록 ID 배타 락 순서로 잠근 뒤 처리하고,
 * 락을 제때 얻지 못하면 503, 삭제된 박람회는 404, 등록 ID의 박람회·참가자·방문일이 요청과 다르면 409다.
 * 이전 세대(generation이 더 작은) 요청은 아무것도 바꾸지 않고 성공으로 끝난다.
 */
interface FieldVisitService {
    /**
     * 등록 하나의 상태(`ACTIVE`/`CANCELLED`)를 반영한다. 같은 세대의 CANCELLED는 최종 상태라 이후 같은 세대 요청은 무시한다.
     * 같은 세대·같은 operationId가 다른 상태를 보내거나, 같은 세대에 HELD 보호가 있는데 CANCELLED가 오면 409다.
     */
    fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: ProjectFieldVisitReqDto,
    )

    /**
     * 그 방문일의 취소 가능 여부를 읽는다: 그날 첫입장(User entry-status, 입장 시도 기록, entry log), 그날 프로그램 출석(연결 연수자 포함),
     * 보호 상태(NONE/HELD/COMMITTED), 연결 연수자. 소유 확인은 User cancellation-status로 하고 누적 `entered`는 쓰지 않는다.
     * generation이 다르면 409, User가 모르는 참가자면 404, 결과가 불확실한 입장 시도가 있거나 User 응답이 맞지 않으면 503이다.
     */
    fun status(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: FieldVisitCancellationReqDto,
    ): FieldVisitCancellationResDto

    /**
     * 진행 중인 스캔을 기다린 뒤(연결 연수자 배타 락까지) 그 방문일 보호를 HELD로 건다. HELD 동안 그날의 참가자·연결 연수자 스캔은 거절된다.
     * 그날 입장·출석이 있거나 등록이 ACTIVE가 아니면 409이고, 다른 작업의 보호나 이미 중단된 같은 작업도 409다.
     * 같은 operationId가 이미 COMMITTED면 그대로 성공이다.
     */
    fun acquire(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FieldVisitCancellationReqDto,
    )

    /** 이 작업의 HELD 보호를 확정하고 그 FIELD 등록만 CANCELLED로 바꾼다. 다른 PRE/FIELD는 그대로다. 보호가 없거나 중단됐으면 409, 이미 확정이면 성공이다. */
    fun commit(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    )

    /** 이 작업을 중단으로 기록해(tombstone) 보호를 풀고 늦게 온 같은 작업의 acquire를 409로 막는다. 이미 확정된 작업이면 409다. */
    fun abort(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    )
}
