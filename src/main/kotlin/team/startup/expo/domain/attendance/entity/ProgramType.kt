package team.startup.expo.domain.attendance.entity

/** 일반 프로그램과 연수 프로그램은 ID 공간이 서로 달라 구분해서 다룬다. */
enum class ProgramType {
    STANDARD,
    TRAINING,
    ;

    fun keyOf(programId: Long): String = "$name:$programId"
}
