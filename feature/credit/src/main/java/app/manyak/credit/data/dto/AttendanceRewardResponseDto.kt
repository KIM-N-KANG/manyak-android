package app.manyak.credit.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceRewardResponseDto(
    val rewarded: Boolean,
    val amount: Long? = null,
)
