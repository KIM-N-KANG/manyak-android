package app.manyak.notification.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRegisterRequestDto(
    val token: String,
    /** 기본값으로 두지 않는다 — 직렬화가 기본값을 생략해 서버가 platform 누락 400 을 돌려준다. */
    val platform: String,
)

@Serializable
data class PushTokenDeleteRequestDto(
    val token: String,
)
