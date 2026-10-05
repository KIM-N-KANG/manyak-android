package app.manyak.create.general.data.api

import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralEndingInput
import app.manyak.create.general.entity.GeneralEventInput
import app.manyak.create.general.entity.GeneralImageError
import app.manyak.create.general.entity.GeneralImageInput
import app.manyak.create.general.entity.GeneralModerationIssue
import app.manyak.create.general.entity.GeneralStartInput
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStorySettings
import app.manyak.create.general.entity.GeneralSubmission

internal fun GeneralStoryEditDto.toEditor(): GeneralStoryEditor =
    GeneralStoryEditor(content = toContent(), submission = submission?.toDomain())

internal fun GeneralSubmissionDto.toEditor(): GeneralStoryEditor =
    GeneralStoryEditor(
        content = payload.toContent(),
        submission = GeneralSubmissionMetadataDto(submissionId, status, issues, errorCode, imageErrors).toDomain(),
        storyId = storyId,
        kind = kind,
    )

private fun GeneralStoryEditDto.toContent(): GeneralStoryContent =
    GeneralStoryContent(
        title = title,
        oneLineIntro = oneLineIntro.orEmpty(),
        description = description,
        genres = genres,
        storySettings =
            GeneralStorySettings(
                worldSetting = storySettings.worldSetting.orEmpty(),
                ruleSetting = storySettings.ruleSetting.orEmpty(),
                userRoleSetting = storySettings.userRoleSetting.orEmpty(),
                characterSetting = storySettings.characterSetting.orEmpty(),
            ),
        startSettings = startSettings.map { it.toDomain() },
        mainEvents = mainEvents.map { GeneralEventInput(it.name, it.description, it.keySentence) },
        visibility = visibility,
        thumbnailImage =
            if (thumbnailUrl != null || thumbnailObjectKey != null) {
                GeneralStoryImage(objectKey = thumbnailObjectKey, previewUrl = thumbnailUrl.orEmpty())
            } else {
                null
            },
        characters = characters.map { it.toDomain() },
    )

private fun GeneralStartSettingDto.toDomain(): GeneralStartInput =
    GeneralStartInput(
        id = id,
        name = name,
        prologue = prologue.orEmpty(),
        startSituation = startSituation.orEmpty(),
        suggestedInputs = suggestedInputs,
        endings =
            endings.map {
                GeneralEndingInput(it.name, it.requirement.minTurns, it.requirement.achievementCondition, it.epilogue)
            },
    )

private fun GeneralCharacterResponseDto.toDomain(): GeneralCharacterInput =
    GeneralCharacterInput(
        id = id,
        name = name,
        description = description,
        images = images.map { GeneralImageInput(it.id, it.objectKey, it.imageName, it.imageUrl) },
    )

private fun GeneralSubmissionMetadataDto.toDomain(): GeneralSubmission =
    GeneralSubmission(
        id = submissionId,
        status = status,
        issues = issues.map { GeneralModerationIssue(it.path, it.type, it.rule, it.reason) },
        errorCode = errorCode,
        imageErrors = imageErrors.map { GeneralImageError(it.path, it.errorCode) },
    )
