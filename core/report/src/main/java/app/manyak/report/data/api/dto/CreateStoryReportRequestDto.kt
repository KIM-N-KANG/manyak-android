package app.manyak.report.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateStoryReportRequestDto(
    val reason: String,
    val detail: String? = null,
)
