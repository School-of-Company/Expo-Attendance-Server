package team.startup.expo.domain.attendance.service

interface PublishEntryEventsService {
    /** 발행하지 않은 입장 이벤트를 Kafka로 보내고 발행한 개수를 돌려준다. */
    fun publishPending(): Int

    /** 발행을 마친 지 보관 기간이 지난 이벤트를 지우고 지운 개수를 돌려준다. */
    fun deleteExpired(): Int
}
