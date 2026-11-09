@file:Suppress("MagicNumber")

package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralEndingInput
import app.manyak.create.general.entity.GeneralEventInput
import app.manyak.create.general.entity.GeneralImageInput
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralStartInput
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage

fun normalizeGeneralCharacterDescription(value: String): String = value.replace(Regex("[\\t\\r\\n]"), " ").trim()

fun buildGeneralStoryContent(form: GeneralStoryForm): GeneralStoryContent =
    GeneralStoryContent(
        title = form.title.trim(),
        oneLineIntro = form.oneLineIntro.trim(),
        description = form.description.trim().takeIf(String::isNotEmpty),
        genres = form.genres.map(String::trim),
        protagonistName =
            form.protagonist.name
                .trim()
                .takeIf(String::isNotEmpty),
        storySettings = buildStorySettings(form),
        startSettings = form.startSettings.map(::toStartInput),
        mainEvents =
            form.mainEvents.map {
                GeneralEventInput(it.name.trim(), it.description.trim(), it.keySentence.trim())
            },
        visibility = form.visibility,
        thumbnailImage = form.cover,
        characters =
            form.supporting.map { character ->
                GeneralCharacterInput(
                    name = character.name.trim(),
                    description =
                        normalizeGeneralCharacterDescription(character.description).takeIf(String::isNotEmpty),
                    images =
                        character.image
                            ?.objectKey
                            ?.takeIf(String::isNotBlank)
                            ?.let {
                                listOf(GeneralImageInput(objectKey = it, imageName = "${character.name.trim()}_기본"))
                            }.orEmpty(),
                )
            },
    )

private fun toStartInput(setting: GeneralStartSetting) =
    GeneralStartInput(
        name = setting.name.trim(),
        prologue = setting.prologue.trim(),
        startSituation = setting.situation.trim(),
        suggestedInputs = setting.suggestedInputs.map(String::trim),
        endings =
            setting.endings.map {
                GeneralEndingInput(
                    name = it.name.trim(),
                    minTurns = it.minTurns.toIntOrNull(),
                    achievementCondition = it.condition.trim(),
                    epilogue = it.epilogue.trim(),
                )
            },
    )

fun restoreGeneralStoryForm(
    content: GeneralStoryContent,
    matchByPosition: Boolean = true,
): GeneralStoryForm {
    val settings = parseStorySettings(content.storySettings)
    val parsed = parseSupporting(content.storySettings.characterSetting)
    val supporting = parsed.ifEmpty { content.characters.map { GeneralCharacter(name = it.name) } }
    val remaining = content.characters.toMutableList()
    return GeneralStoryForm(
        title = content.title,
        oneLineIntro = content.oneLineIntro,
        world = settings.world,
        progression = settings.progression,
        descriptionRatio = settings.descriptionRatio,
        protagonist =
            parseProtagonist(content.storySettings.userRoleSetting).copy(name = content.protagonistName.orEmpty()),
        supporting =
            supporting.ifEmpty { listOf(GeneralCharacter()) }.mapIndexed { index, character ->
                val server =
                    remaining.find { it.name.trim() == character.name.trim() }
                        ?: if (matchByPosition) {
                            content.characters.getOrNull(index)?.takeIf { it in remaining }
                        } else {
                            null
                        }
                if (server != null) remaining.remove(server)
                character.copy(
                    serverId = server?.id,
                    description = server?.description.orEmpty(),
                    image = server?.images?.firstOrNull()?.toFormImage(),
                )
            },
        startSettings = content.startSettings.map(::restoreStartSetting).ifEmpty { listOf(GeneralStartSetting()) },
        mainEvents =
            content.mainEvents.map {
                GeneralMainEvent(name = it.name, description = it.description, keySentence = it.keySentence)
            },
        genres = content.genres,
        description = content.description.orEmpty(),
        visibility = content.visibility,
        cover = content.thumbnailImage,
    )
}

private fun restoreStartSetting(setting: GeneralStartInput) =
    GeneralStartSetting(
        serverId = setting.id,
        name = setting.name,
        prologue = setting.prologue,
        situation = setting.startSituation,
        suggestedInputs = List(3) { setting.suggestedInputs.getOrElse(it) { "" } },
        endings =
            setting.endings.map {
                GeneralEnding(
                    name = it.name,
                    minTurns = it.minTurns?.toString().orEmpty(),
                    condition = it.achievementCondition,
                    epilogue = it.epilogue,
                )
            },
    )

private fun GeneralImageInput.toFormImage() =
    GeneralStoryImage(objectKey = objectKey, previewUrl = imageUrl, serverId = id)
