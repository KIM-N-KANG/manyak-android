package app.manyak.notification.settings.data.api.dto

import kotlinx.serialization.Serializable

/** 요청·응답이 같은 스키마다. 기본값을 두지 않는다 — 직렬화가 기본값 필드를 생략하면 서버가 400 을 돌려준다. */
@Serializable
data class PushSettingsDto(
    val servicePush: Boolean,
    val marketingPush: Boolean,
    val marketingNightPush: Boolean,
)
