package app.manyak.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceRewardResponseDto(
    val rewarded: Boolean,
    val amount: Long? = null,
)
