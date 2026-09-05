package app.manyak.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeResponseDto(
    val id: String,
    val nickname: String,
    val profileImageUrl: String? = null,
    val profileThumbnailBase64: String? = null,
    val status: String,
    val creditBalance: Long = 0,
    val attendedToday: Boolean = false,
    val linkedProviders: List<String> = emptyList(),
)
