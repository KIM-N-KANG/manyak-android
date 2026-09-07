package app.manyak.my.invite.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RedeemInviteCodeRequestDto(
    val code: String,
)
