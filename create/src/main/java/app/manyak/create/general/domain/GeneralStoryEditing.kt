package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralEditCharacter
import app.manyak.create.general.entity.GeneralImageInput
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditBase
import app.manyak.create.general.entity.GeneralStoryEditForm
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryPatch

fun restoreGeneralStoryEditForm(
    content: GeneralStoryContent,
    sendAll: Boolean = false,
): GeneralStoryEditForm {
    val form = restoreGeneralStoryForm(content, matchByPosition = false)
    val unmatched = form.supporting.toMutableList()
    val characters =
        content.characters.map { source ->
            val character = unmatched.find { it.name.trim() == source.name.trim() }
            unmatched.remove(character)
            GeneralEditCharacter(formId = character?.id, source = source)
        }
    return GeneralStoryEditForm(
        form = form,
        base =
            GeneralStoryEditBase(
                characters = characters,
                baseline = buildEditCandidate(form, characters),
                originalForm = form,
                sendAll = sendAll,
            ),
    )
}

fun shouldDeleteGeneralThumbnail(
    form: GeneralStoryForm,
    base: GeneralStoryEditBase,
): Boolean = base.originalForm.cover != null && form.cover == null

fun buildGeneralStoryPatch(
    form: GeneralStoryForm,
    base: GeneralStoryEditBase,
): GeneralStoryPatch {
    val candidate = buildEditCandidate(form, base.characters)
    val baseline = base.baseline

    fun <T> changed(
        current: T?,
        original: T?,
    ): T? = current.takeIf { base.sendAll || it != original }
    return GeneralStoryPatch(
        title = changed(candidate.title, baseline.title),
        oneLineIntro = changed(candidate.oneLineIntro, baseline.oneLineIntro),
        description = changed(candidate.description, baseline.description),
        genres = changed(candidate.genres, baseline.genres),
        storySettings = changed(candidate.storySettings, baseline.storySettings),
        startSettings = changed(candidate.startSettings, baseline.startSettings),
        mainEvents = changed(candidate.mainEvents, baseline.mainEvents),
        visibility = changed(candidate.visibility, baseline.visibility),
        thumbnailObjectKey = changed(candidate.thumbnailObjectKey, baseline.thumbnailObjectKey),
        characters = changed(candidate.characters, baseline.characters),
    )
}

private fun buildEditCandidate(
    form: GeneralStoryForm,
    characters: List<GeneralEditCharacter>,
): GeneralStoryPatch {
    val content = buildGeneralStoryContent(form)
    return GeneralStoryPatch(
        title = content.title,
        oneLineIntro = content.oneLineIntro,
        description = content.description.orEmpty(),
        genres = content.genres,
        storySettings = content.storySettings,
        startSettings =
            content.startSettings.mapIndexed { index, setting ->
                setting.copy(id = form.startSettings[index].serverId)
            },
        mainEvents = content.mainEvents,
        visibility = content.visibility,
        thumbnailObjectKey = form.cover?.objectKey?.takeIf(String::isNotBlank),
        characters =
            form.supporting.map { character ->
                editCharacter(character, characters.find { it.formId == character.id }?.source)
            } +
                characters.filter { it.formId == null }.map {
                    it.source.copy(description = null, images = it.source.images.map(::imageReference))
                },
    )
}

private fun editCharacter(
    character: GeneralCharacter,
    original: GeneralCharacterInput?,
): GeneralCharacterInput {
    val kept = original?.images.orEmpty().map(::imageReference)
    val uploadedKey =
        character.image?.objectKey?.takeIf { it.isNotBlank() && it != original?.images?.firstOrNull()?.objectKey }
    val images =
        when {
            uploadedKey != null ->
                listOf(GeneralImageInput(objectKey = uploadedKey, imageName = "${character.name.trim()}_기본")) +
                    kept.drop(1)
            character.image != null -> kept
            else -> kept.drop(1)
        }
    return GeneralCharacterInput(
        id = original?.id,
        name = character.name.trim(),
        description = normalizeGeneralCharacterDescription(character.description),
        images = images,
    )
}

private fun imageReference(image: GeneralImageInput): GeneralImageInput =
    if (image.id != null) {
        GeneralImageInput(id = image.id)
    } else {
        image.copy(imageUrl = "")
    }
