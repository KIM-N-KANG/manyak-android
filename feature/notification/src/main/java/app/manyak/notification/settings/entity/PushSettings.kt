package app.manyak.notification.settings.entity

/** 회원의 푸시 수신 동의. 광고·야간의 정본은 서버의 동의 시각이지만 앱에는 boolean 만 온다. */
data class PushSettings(
    val servicePush: Boolean,
    val marketingPush: Boolean,
    val marketingNightPush: Boolean,
)
