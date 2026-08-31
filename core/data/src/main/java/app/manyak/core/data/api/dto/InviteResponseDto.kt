package app.manyak.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class InviteResponseDto(
    val inviteCode: String? = null,
    val monthlyRewardCount: Int? = null,
    val monthlyRewardLimit: Int? = null,
)
