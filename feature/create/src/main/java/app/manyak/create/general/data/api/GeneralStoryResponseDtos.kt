package app.manyak.create.general.data.api

import kotlinx.serialization.Serializable

@Serializable
data class GeneralStoryEditDto(
    val title: String,
    val oneLineIntro: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val protagonistName: String? = null,
    val storySettings: GeneralSettingsDto,
    val startSettings: List<GeneralStartSettingDto> = emptyList(),
    val mainEvents: List<GeneralMainEventDto> = emptyList(),
    val visibility: String,
    val thumbnailUrl: String? = null,
    val thumbnailObjectKey: String? = null,
    val thumbnailModerationStatus: String = "APPROVED",
    val characters: List<GeneralCharacterResponseDto> = emptyList(),
    val submission: GeneralSubmissionMetadataDto? = null,
)

@Serializable
data class GeneralCharacterResponseDto(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val images: List<GeneralCharacterImageResponseDto> = emptyList(),
)

@Serializable
data class GeneralCharacterImageResponseDto(
    val id: String? = null,
    val imageName: String,
    val imageUrl: String,
    val moderationStatus: String = "APPROVED",
    val objectKey: String? = null,
)

@Serializable
data class GeneralAcceptedDto(
    val submissionId: String,
    val status: String,
)

@Serializable
data class GeneralSubmissionMetadataDto(
    val submissionId: String,
    val status: String,
    val issues: List<GeneralModerationIssueDto> = emptyList(),
    val errorCode: String? = null,
    val imageErrors: List<GeneralImageErrorDto> = emptyList(),
)

@Serializable
data class GeneralSubmissionDto(
    val submissionId: String,
    val storyId: String? = null,
    val kind: String,
    val payload: GeneralStoryEditDto,
    val status: String,
    val issues: List<GeneralModerationIssueDto> = emptyList(),
    val errorCode: String? = null,
    val imageErrors: List<GeneralImageErrorDto> = emptyList(),
)

@Serializable
data class GeneralModerationIssueDto(
    val path: String,
    val type: String,
    val rule: String,
    val reason: String,
)

@Serializable
data class GeneralImageErrorDto(
    val path: String,
    val errorCode: String,
)
