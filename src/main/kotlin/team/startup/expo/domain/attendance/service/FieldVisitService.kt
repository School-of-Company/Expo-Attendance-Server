package team.startup.expo.domain.attendance.service

import team.startup.expo.domain.attendance.presentation.dto.request.FieldVisitCancellationReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.FinishFieldVisitGuardReqDto
import team.startup.expo.domain.attendance.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.attendance.presentation.dto.response.FieldVisitCancellationResDto
import java.util.UUID

interface FieldVisitService {
    fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: ProjectFieldVisitReqDto,
    )

    fun status(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        request: FieldVisitCancellationReqDto,
    ): FieldVisitCancellationResDto

    fun acquire(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FieldVisitCancellationReqDto,
    )

    fun commit(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    )

    fun abort(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        operationId: UUID,
        request: FinishFieldVisitGuardReqDto,
    )
}
