package team.startup.expo.global.client.expo

/** 박람회 서비스 `GET /internal/expo/{expoId}/standard-programs/{programId}` 응답. */
data class StandardProgramResDto(
    val id: Long,
    val title: String,
    /** 시작 일시(시간대 없는 한국 시간 `2026-10-31T10:30`, 예전 행은 `2026-10-31 10:30`). 없거나 읽을 수 없으면 시간대 검사를 건너뛴다. */
    val startedAt: String? = null,
    val endedAt: String? = null,
)

/** 박람회 서비스 `POST /internal/expo/{expoId}/training-programs/batch` 요청. 한 번에 100개까지다. */
data class TrainingProgramBatchReqDto(
    val programIds: List<Long>,
)

data class TrainingProgramResDto(
    val id: Long,
    val title: String,
)
