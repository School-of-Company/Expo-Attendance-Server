package team.startup.expo.domain.qr.entity

/** 종이 QR 입장을 기록할 때 박람회 기간을 확인했는지. 입장 자체는 어느 값이든 기록되어 있다. */
enum class QrEntryPeriodCheck {
    /** 기간 안에서 확인하고 기록했다. */
    VERIFIED,

    /** 박람회 서비스가 응답하지 못해 확인 없이 기록했다. 나중에 다시 확인한다. */
    PENDING,

    /** 나중에 확인해 보니 입장한 날이 박람회 기간 밖이었다. 운영에서 확인해야 한다. */
    OUT_OF_PERIOD,

    /** 나중에 확인해 보니 박람회가 없었다. */
    EXPO_NOT_FOUND,
}
