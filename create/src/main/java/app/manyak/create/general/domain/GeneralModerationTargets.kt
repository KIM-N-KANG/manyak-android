package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab

fun generalModerationTarget(
    path: String,
    submittedForm: GeneralStoryForm,
): GeneralFieldTarget? =
    directTarget(path)
        ?: characterTarget(path, submittedForm)
        ?: startTarget(path, submittedForm)
        ?: eventTarget(path, submittedForm)

@Suppress("CyclomaticComplexMethod")
private fun directTarget(path: String): GeneralFieldTarget? =
    when {
        path == "title" -> GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.TITLE)
        path == "oneLineIntro" -> GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.ONE_LINE_INTRO)
        path in listOf("thumbnailUrl", "thumbnailObjectKey") ->
            GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.COVER)
        path == "storySettings.worldSetting" -> GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.WORLD)
        path == "storySettings.ruleSetting" -> GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.PROGRESSION)
        path == "storySettings.userRoleSetting" -> GeneralFieldTarget(GeneralTab.PROTAGONIST, GeneralField.ITEMS)
        path == "storySettings.characterSetting" -> GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.ITEMS)
        path == "description" -> GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.DESCRIPTION)
        path == "genres" || Regex("^genres\\[\\d+\\]$").matches(path) ->
            GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.GENRES)
        else -> null
    }

private fun characterTarget(
    path: String,
    form: GeneralStoryForm,
): GeneralFieldTarget? {
    val match = Regex("^characters\\[(\\d+)\\]\\.(.+)$").matchEntire(path) ?: return null
    val character = form.supporting.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: return null
    val part = match.groupValues[2]
    val field =
        when {
            part == "name" || Regex("^images\\[\\d+\\]\\.imageName$").matches(part) -> GeneralField.NAME
            part == "description" -> GeneralField.CHARACTER_DESCRIPTION
            Regex("^images\\[\\d+\\]\\.(imageUrl|objectKey)$").matches(part) -> GeneralField.IMAGE
            else -> return null
        }
    return GeneralFieldTarget(GeneralTab.SUPPORTING, field, itemId = character.id)
}

@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun startTarget(
    path: String,
    form: GeneralStoryForm,
): GeneralFieldTarget? {
    val match = Regex("^startSettings\\[(\\d+)\\]\\.(.+)$").matchEntire(path) ?: return null
    val start = form.startSettings.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: return null
    val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, itemId = start.id, startId = start.id)
    val part = match.groupValues[2]
    val field =
        when (part) {
            "name" -> GeneralField.NAME
            "prologue" -> GeneralField.PROLOGUE
            "startSituation" -> GeneralField.SITUATION
            else -> null
        }
    if (field != null) return target.copy(field = field)
    val suggested = Regex("^suggestedInputs\\[(\\d+)\\]$").matchEntire(part)
    if (suggested != null) {
        val index = suggested.groupValues[1].toIntOrNull() ?: return null
        return target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index)
    }
    val ending = Regex("^endings\\[(\\d+)\\]\\.(.+)$").matchEntire(part) ?: return null
    val endingItem = start.endings.getOrNull(ending.groupValues[1].toIntOrNull() ?: -1) ?: return null
    val endingField =
        when (ending.groupValues[2]) {
            "name" -> GeneralField.NAME
            "requirement.minTurns" -> GeneralField.MIN_TURNS
            "requirement.achievementCondition" -> GeneralField.CONDITION
            "epilogue" -> GeneralField.EPILOGUE
            else -> return null
        }
    return target.copy(field = endingField, itemId = endingItem.id)
}

private fun eventTarget(
    path: String,
    form: GeneralStoryForm,
): GeneralFieldTarget? {
    val match = Regex("^mainEvents\\[(\\d+)\\]\\.(name|description|keySentence)$").matchEntire(path) ?: return null
    val event = form.mainEvents.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: return null
    val field =
        when (match.groupValues[2]) {
            "name" -> GeneralField.NAME
            "description" -> GeneralField.EVENT_DESCRIPTION
            else -> GeneralField.KEY_SENTENCE
        }
    return GeneralFieldTarget(GeneralTab.EVENTS, field, itemId = event.id)
}

fun generalFieldValue(
    form: GeneralStoryForm,
    target: GeneralFieldTarget,
): Any? =
    when (target.tab) {
        GeneralTab.PROFILE -> profileValue(form, target.field)
        GeneralTab.SETTINGS ->
            if (target.field == GeneralField.WORLD) form.world else form.progression to form.descriptionRatio
        GeneralTab.PROTAGONIST -> characterValue(form.protagonist, target.field)
        GeneralTab.SUPPORTING ->
            if (target.field == GeneralField.ITEMS) {
                form.supporting.map { Triple(it.name, it.gender, it.feature) }
            } else {
                form.supporting.find { it.id == target.itemId }?.let { characterValue(it, target.field) }
            }
        GeneralTab.START -> startValue(form, target)
        GeneralTab.EVENTS -> eventValue(form, target)
        GeneralTab.PUBLISH -> if (target.field == GeneralField.GENRES) form.genres else form.description
    }

private fun profileValue(
    form: GeneralStoryForm,
    field: GeneralField,
): Any? =
    when (field) {
        GeneralField.TITLE -> form.title
        GeneralField.ONE_LINE_INTRO -> form.oneLineIntro
        GeneralField.COVER -> form.cover
        else -> null
    }

private fun characterValue(
    character: GeneralCharacter,
    field: GeneralField,
): Any? =
    when (field) {
        GeneralField.NAME -> character.name
        GeneralField.GENDER -> character.gender
        GeneralField.FEATURE -> character.feature
        GeneralField.CHARACTER_DESCRIPTION -> character.description
        GeneralField.IMAGE -> character.image
        GeneralField.ITEMS -> Triple(character.name, character.gender, character.feature)
        else -> null
    }

@Suppress("CyclomaticComplexMethod")
private fun startValue(
    form: GeneralStoryForm,
    target: GeneralFieldTarget,
): Any? {
    val start = form.startSettings.find { it.id == target.startId } ?: return null
    val ending = start.endings.find { it.id == target.itemId }
    return if (ending == null && target.itemId == start.id) {
        when (target.field) {
            GeneralField.NAME -> start.name
            GeneralField.PROLOGUE -> start.prologue
            GeneralField.SITUATION -> start.situation
            GeneralField.SUGGESTED_INPUT -> start.suggestedInputs.getOrNull(target.inputIndex ?: -1)
            GeneralField.ITEMS -> start
            else -> null
        }
    } else {
        when (target.field) {
            GeneralField.NAME -> ending?.name
            GeneralField.MIN_TURNS -> ending?.minTurns
            GeneralField.CONDITION -> ending?.condition
            GeneralField.EPILOGUE -> ending?.epilogue
            else -> null
        }
    }
}

private fun eventValue(
    form: GeneralStoryForm,
    target: GeneralFieldTarget,
): Any? {
    val event = form.mainEvents.find { it.id == target.itemId } ?: return null
    return when (target.field) {
        GeneralField.NAME -> event.name
        GeneralField.EVENT_DESCRIPTION -> event.description
        GeneralField.KEY_SENTENCE -> event.keySentence
        else -> null
    }
}
