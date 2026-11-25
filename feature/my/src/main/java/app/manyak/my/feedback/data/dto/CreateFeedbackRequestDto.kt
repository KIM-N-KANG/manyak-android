package app.manyak.my.feedback.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFeedbackRequestDto(
    val body: String,
    val email: String? = null,
    val platform: String,
    val appVersion: String? = null,
)
