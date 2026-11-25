package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStorySettings
import app.manyak.create.general.entity.GeneralTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStoryEditValidationTest {
    @Test
    fun title_and_visibility_edits_do_not_require_fixing_unchanged_legacy_settings() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        assertTrue(validateGeneralStoryEdit(edit.form.copy(title = "새로운 제목"), edit.base).isEmpty())
        assertTrue(validateGeneralStoryEdit(edit.form.copy(visibility = "PUBLIC"), edit.base).isEmpty())
    }

    @Test
    fun a_changed_field_still_obeys_its_limits() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        val errors = validateGeneralStoryEdit(edit.form.copy(title = "가"), edit.base)
        assertEquals(GeneralField.TITLE, errors.single().target.field)
    }

    @Test
    fun a_setting_change_validates_all_four_settings_sent_together() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        val errors = validateGeneralStoryEdit(edit.form.copy(world = "새로운 세계관"), edit.base)
        assertTrue(errors.any { it.target.tab == GeneralTab.PROTAGONIST })
        assertTrue(errors.any { it.target.tab == GeneralTab.SUPPORTING && it.target.field == GeneralField.GENDER })
        assertFalse(errors.any { it.target.tab == GeneralTab.START })
    }

    @Test
    fun description_change_does_not_require_unmodified_character_gender_or_features() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        val form = edit.form.copy(supporting = edit.form.supporting.map { it.copy(description = "새로운 소개") })
        assertTrue(validateGeneralStoryEdit(form, edit.base).isEmpty())
    }

    @Test
    fun rejected_edit_resubmission_validates_the_whole_payload() {
        val edit = restoreGeneralStoryEditForm(legacyContent(), sendAll = true)
        val errors = validateGeneralStoryEdit(edit.form.copy(visibility = "PUBLIC"), edit.base)
        assertTrue(errors.any { it.target.field == GeneralField.WORLD })
        assertTrue(errors.any { it.target.tab == GeneralTab.START })
    }

    private fun legacyContent() =
        GeneralStoryContent(
            title = "기존 제목",
            oneLineIntro = "기존 소개",
            storySettings =
                GeneralStorySettings(
                    worldSetting = "가".repeat(5001),
                    ruleSetting = "기존 전개",
                    userRoleSetting = "오래된 주인공 설명",
                    characterSetting = "# 등장인물\n\n## 주변 인물\n기존 인물 설명",
                ),
        )
}
