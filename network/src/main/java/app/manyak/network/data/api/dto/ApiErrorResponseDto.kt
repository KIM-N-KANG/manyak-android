package app.manyak.network.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponseDto(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)
