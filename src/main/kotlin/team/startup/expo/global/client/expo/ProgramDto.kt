package team.startup.expo.global.client.expo

/** 박람회 서비스 `GET /internal/expo/{expoId}/standard-programs/{programId}` 응답. */
data class StandardProgramResDto(
    val id: Long,
    /** 시작 일시. Expo 동기 조회는 UTC instant(`2026-10-31T01:30:00Z`), 프로젝션은 시간대 없는 한국 시간(`2026-10-31T10:30`)이다. 없거나 읽을 수 없으면 시간대 검사를 건너뛴다. */
    val startedAt: String? = null,
    val endedAt: String? = null,
)

/** 박람회 서비스 `POST /internal/expo/{expoId}/training-programs/batch` 요청. 한 번에 100개까지다. */
data class TrainingProgramBatchReqDto(
    val programIds: List<Long>,
)

/** 박람회 서비스 `POST /internal/expo/{expoId}/training-programs/batch` 응답 항목. 있는지만 보므로 `id`만 읽는다. */
data class TrainingProgramResDto(
    val id: Long,
)
