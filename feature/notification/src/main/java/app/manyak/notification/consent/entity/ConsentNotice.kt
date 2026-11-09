package app.manyak.notification.consent.entity

/** 광고 알림 동의 상태를 바꾼 한 번의 의사 표시. 어느 쪽으로 바꿨는지가 통지 문구를 정한다. */
enum class ConsentChange {
    MARKETING_ON,
    MARKETING_OFF,
    NIGHT_ON,
    NIGHT_OFF,
}

/**
 * 동의·철회 처리 결과 통지. 전송자·일시·처리 결과를 사용자에게 알려야 하는데, 서버 응답에는 동의 시각이
 * 없어 사용자가 의사를 표시한 시점을 앱이 기록한다.
 */
data class ConsentNotice(
    val change: ConsentChange,
    val atEpochMillis: Long,
)
