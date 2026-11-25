package app.manyak.my.invite.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class InviteResponseDto(
    val inviteCode: String? = null,
    val monthlyRewardCount: Int? = null,
    val monthlyRewardLimit: Int? = null,
)
