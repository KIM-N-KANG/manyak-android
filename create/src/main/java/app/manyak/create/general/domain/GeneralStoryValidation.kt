@file:Suppress("MagicNumber")

package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralErrorReason
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab

fun validateGeneralStoryForm(
    form: GeneralStoryForm,
    allowedGenres: Set<String>? = null,
): List<GeneralFieldError> =
    buildList {
        text(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.TITLE), form.title, 100)
        text(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.ONE_LINE_INTRO), form.oneLineIntro, 255)
        text(GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.WORLD), form.world, 5000)
        text(GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.PROGRESSION), form.progression, 1000)
        validateProtagonist(form.protagonist)
        validateSupporting(form)
        count(GeneralFieldTarget(GeneralTab.START, GeneralField.ITEMS), form.startSettings.size, 1..3)
        form.startSettings.forEach(::validateStart)
        validateEvents(form)
        validatePublishing(form, allowedGenres)
    }

private fun MutableList<GeneralFieldError>.validateProtagonist(character: GeneralCharacter) {
    val target = GeneralFieldTarget(GeneralTab.PROTAGONIST, GeneralField.NAME)
    text(target, character.name, 30)
    if (character.gender == null) {
        add(GeneralFieldError(target.copy(field = GeneralField.GENDER), GeneralErrorReason.GENDER_REQUIRED))
    }
    text(target.copy(field = GeneralField.FEATURE), character.feature, 1000)
}

private fun MutableList<GeneralFieldError>.validateSupporting(form: GeneralStoryForm) {
    val tab = GeneralTab.SUPPORTING
    count(GeneralFieldTarget(tab, GeneralField.ITEMS), form.supporting.size, 1..5)
    val names = mutableSetOf(form.protagonist.name.trim())
    form.supporting.forEach { character ->
        val target = GeneralFieldTarget(tab, GeneralField.NAME, itemId = character.id)
        text(target, character.name, 30, duplicate = !names.add(character.name.trim()))
        if (character.gender == null) {
            add(GeneralFieldError(target.copy(field = GeneralField.GENDER), GeneralErrorReason.GENDER_REQUIRED))
        }
        text(
            target.copy(field = GeneralField.CHARACTER_DESCRIPTION),
            normalizeGeneralCharacterDescription(character.description),
            80,
            required = false,
        )
        text(target.copy(field = GeneralField.FEATURE), character.feature, 1000, required = false)
    }
}

private fun MutableList<GeneralFieldError>.validateStart(start: GeneralStartSetting) {
    val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, itemId = start.id, startId = start.id)
    text(target, start.name, 100)
    text(target.copy(field = GeneralField.PROLOGUE), start.prologue, 1000)
    text(target.copy(field = GeneralField.SITUATION), start.situation, 1000)
    count(target.copy(field = GeneralField.SUGGESTED_INPUT), start.suggestedInputs.size, 3..3)
    start.suggestedInputs.forEachIndexed { index, value ->
        text(target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index), value, 200)
    }
    count(target.copy(field = GeneralField.ITEMS), start.endings.size, 0..3)
    val names = mutableSetOf<String>()
    start.endings.forEach { ending ->
        validateEnding(ending, target.copy(itemId = ending.id), !names.add(ending.name.trim()))
    }
}

private fun MutableList<GeneralFieldError>.validateEnding(
    ending: GeneralEnding,
    target: GeneralFieldTarget,
    duplicate: Boolean,
) {
    text(target, ending.name, 100, duplicate = duplicate)
    val turnsTarget = target.copy(field = GeneralField.MIN_TURNS)
    val turns = ending.minTurns.toIntOrNull()
    when {
        ending.minTurns.isBlank() -> add(GeneralFieldError(turnsTarget, GeneralErrorReason.REQUIRED))
        turns == null || turns !in 0..50 -> add(GeneralFieldError(turnsTarget, GeneralErrorReason.INVALID_NUMBER, 50))
    }
    text(target.copy(field = GeneralField.CONDITION), ending.condition, 500)
    text(target.copy(field = GeneralField.EPILOGUE), ending.epilogue, 500)
}

private fun MutableList<GeneralFieldError>.validateEvents(form: GeneralStoryForm) {
    val tab = GeneralTab.EVENTS
    count(GeneralFieldTarget(tab, GeneralField.ITEMS), form.mainEvents.size, 0..10)
    val names = mutableSetOf<String>()
    form.mainEvents.forEach { event ->
        val target = GeneralFieldTarget(tab, GeneralField.NAME, itemId = event.id)
        text(target, event.name, 100, duplicate = !names.add(event.name.trim()))
        text(target.copy(field = GeneralField.EVENT_DESCRIPTION), event.description, 1000)
        text(target.copy(field = GeneralField.KEY_SENTENCE), event.keySentence, 200)
    }
}

private fun MutableList<GeneralFieldError>.validatePublishing(
    form: GeneralStoryForm,
    allowedGenres: Set<String>?,
) {
    val target = GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.GENRES)
    val reason =
        when {
            form.genres.isEmpty() -> GeneralErrorReason.GENRE_REQUIRED
            form.genres.size > 8 -> GeneralErrorReason.INVALID_COUNT
            form.genres.any { it.isBlank() || it.length > 30 || (allowedGenres != null && it !in allowedGenres) } ->
                GeneralErrorReason.INVALID_GENRE
            else -> null
        }
    if (reason != null) add(GeneralFieldError(target, reason, 8))
    text(target.copy(field = GeneralField.DESCRIPTION), form.description, 1000, required = false)
}

@Suppress("LongParameterList")
private fun MutableList<GeneralFieldError>.text(
    target: GeneralFieldTarget,
    value: String,
    maxLength: Int,
    required: Boolean = true,
    duplicate: Boolean = false,
) {
    val trimmed = value.trim()
    val reason =
        when {
            trimmed.isEmpty() -> if (required) GeneralErrorReason.REQUIRED else null
            duplicate -> GeneralErrorReason.DUPLICATE
            trimmed.length < 2 -> GeneralErrorReason.TOO_SHORT
            trimmed.length > maxLength -> GeneralErrorReason.TOO_LONG
            else -> null
        }
    if (reason != null) add(GeneralFieldError(target, reason, maxLength))
}

private fun MutableList<GeneralFieldError>.count(
    target: GeneralFieldTarget,
    size: Int,
    range: IntRange,
) {
    if (size !in range) add(GeneralFieldError(target, GeneralErrorReason.INVALID_COUNT, range.last))
}
