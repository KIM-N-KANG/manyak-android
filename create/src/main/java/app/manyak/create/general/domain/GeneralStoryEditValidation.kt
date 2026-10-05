package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryEditBase
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryPatch
import app.manyak.create.general.entity.GeneralTab

fun validateGeneralStoryEdit(
    form: GeneralStoryForm,
    base: GeneralStoryEditBase,
    allowedGenres: Set<String>? = null,
): List<GeneralFieldError> {
    val errors = validateGeneralStoryForm(form, allowedGenres)
    if (base.sendAll) return errors
    val patch = buildGeneralStoryPatch(form, base)
    return errors.filter { patch.contains(it.target) }
}

private fun GeneralStoryPatch.contains(target: GeneralFieldTarget): Boolean =
    when (target.tab) {
        GeneralTab.PROFILE ->
            when (target.field) {
                GeneralField.TITLE -> title != null
                GeneralField.ONE_LINE_INTRO -> oneLineIntro != null
                else -> false
            }
        GeneralTab.SETTINGS, GeneralTab.PROTAGONIST -> storySettings != null
        GeneralTab.SUPPORTING -> includesSupporting(target.field)
        GeneralTab.START -> startSettings != null
        GeneralTab.EVENTS -> mainEvents != null
        GeneralTab.PUBLISH ->
            when (target.field) {
                GeneralField.GENRES -> genres != null
                GeneralField.DESCRIPTION -> description != null
                else -> false
            }
    }

private fun GeneralStoryPatch.includesSupporting(field: GeneralField): Boolean =
    when (field) {
        GeneralField.CHARACTER_DESCRIPTION, GeneralField.IMAGE -> characters != null
        GeneralField.GENDER, GeneralField.FEATURE -> storySettings != null
        GeneralField.NAME, GeneralField.ITEMS -> storySettings != null || characters != null
        else -> false
    }
