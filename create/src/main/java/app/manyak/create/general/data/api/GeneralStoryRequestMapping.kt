package app.manyak.create.general.data.api

import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralEventInput
import app.manyak.create.general.entity.GeneralStartInput
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryPatch
import app.manyak.create.general.entity.GeneralStorySettings

internal fun GeneralStoryContent.toRequest(): GeneralStoryRequestDto =
    GeneralStoryRequestDto(
        title = title,
        oneLineIntro = oneLineIntro,
        description = description,
        genres = genres,
        protagonistName = protagonistName,
        storySettings = storySettings.toDto(),
        startSettings = startSettings.map { it.toDto() },
        mainEvents = mainEvents.map { it.toDto() },
        visibility = visibility,
        thumbnailObjectKey = thumbnailImage?.objectKey,
        characters = characters.map { it.toDto() },
    )

internal fun GeneralStoryPatch.toRequest(): GeneralStoryPatchDto =
    GeneralStoryPatchDto(
        title = title,
        oneLineIntro = oneLineIntro,
        description = description,
        genres = genres,
        protagonistName = protagonistName,
        storySettings = storySettings?.toDto(),
        startSettings = startSettings?.map { it.toDto() },
        mainEvents = mainEvents?.map { it.toDto() },
        visibility = visibility,
        thumbnailObjectKey = thumbnailObjectKey,
        characters = characters?.map { it.toDto() },
    )

private fun GeneralStorySettings.toDto(): GeneralSettingsDto =
    GeneralSettingsDto(worldSetting, characterSetting, userRoleSetting, ruleSetting)

private fun GeneralStartInput.toDto(): GeneralStartSettingDto =
    GeneralStartSettingDto(
        id = id,
        name = name,
        prologue = prologue,
        startSituation = startSituation,
        suggestedInputs = suggestedInputs,
        endings =
            endings.map {
                GeneralEndingDto(
                    name = it.name,
                    requirement = GeneralEndingRequirementDto(it.minTurns ?: 0, it.achievementCondition),
                    epilogue = it.epilogue,
                )
            },
    )

private fun GeneralEventInput.toDto(): GeneralMainEventDto = GeneralMainEventDto(name, description, keySentence)

private fun GeneralCharacterInput.toDto(): GeneralCharacterRequestDto =
    GeneralCharacterRequestDto(
        id = id,
        name = name,
        description = description,
        images =
            images.map {
                GeneralCharacterImageRequestDto(it.id, it.objectKey, it.imageName.takeIf(String::isNotBlank))
            },
    )
