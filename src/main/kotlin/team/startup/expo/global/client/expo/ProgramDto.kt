package team.startup.expo.global.client.expo

/** 박람회 서비스 `GET /internal/expo/{expoId}/standard-programs/{programId}` 응답. */
data class StandardProgramResDto(
    val id: Long,
    val title: String,
)

/** 박람회 서비스 `POST /internal/expo/{expoId}/training-programs/batch` 요청. 한 번에 100개까지다. */
data class TrainingProgramBatchReqDto(
    val programIds: List<Long>,
)

data class TrainingProgramResDto(
    val id: Long,
    val title: String,
)
