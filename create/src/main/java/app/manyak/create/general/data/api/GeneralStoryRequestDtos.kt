package app.manyak.create.general.data.api

import kotlinx.serialization.Serializable

@Serializable
data class GeneralStoryRequestDto(
    val title: String,
    val oneLineIntro: String,
    val description: String?,
    val genres: List<String>,
    val protagonistName: String?,
    val storySettings: GeneralSettingsDto,
    val startSettings: List<GeneralStartSettingDto>,
    val mainEvents: List<GeneralMainEventDto>,
    val visibility: String,
    val thumbnailObjectKey: String?,
    val characters: List<GeneralCharacterRequestDto>,
)

@Serializable
data class GeneralStoryPatchDto(
    val title: String? = null,
    val oneLineIntro: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val protagonistName: String? = null,
    val storySettings: GeneralSettingsDto? = null,
    val startSettings: List<GeneralStartSettingDto>? = null,
    val mainEvents: List<GeneralMainEventDto>? = null,
    val visibility: String? = null,
    val thumbnailObjectKey: String? = null,
    val characters: List<GeneralCharacterRequestDto>? = null,
)

@Serializable
data class GeneralSettingsDto(
    val worldSetting: String? = null,
    val characterSetting: String? = null,
    val userRoleSetting: String? = null,
    val ruleSetting: String? = null,
)

@Serializable
data class GeneralStartSettingDto(
    val id: String? = null,
    val name: String,
    val prologue: String? = null,
    val startSituation: String? = null,
    val suggestedInputs: List<String> = emptyList(),
    val endings: List<GeneralEndingDto> = emptyList(),
)

@Serializable
data class GeneralEndingDto(
    val name: String,
    val requirement: GeneralEndingRequirementDto,
    val epilogue: String,
)

@Serializable
data class GeneralEndingRequirementDto(
    val minTurns: Int,
    val achievementCondition: String,
)

@Serializable
data class GeneralMainEventDto(
    val name: String,
    val description: String,
    val keySentence: String,
)

@Serializable
data class GeneralCharacterRequestDto(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val images: List<GeneralCharacterImageRequestDto>? = null,
)

@Serializable
data class GeneralCharacterImageRequestDto(
    val id: String? = null,
    val objectKey: String? = null,
    val imageName: String? = null,
)

@Serializable
data class GeneralImagePresignRequestDto(
    val kind: String,
    val contentType: String,
    val contentLength: Long,
)

@Serializable
data class GeneralImagePresignDto(
    val uploadUrl: String,
    val objectKey: String,
    val expiresInSeconds: Long,
)
