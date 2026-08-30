package app.manyak.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RedeemInviteCodeRequestDto(
    val code: String,
)
