package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralStoryForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneralModerationTargetsTest {
    @Test
    fun review_uses_submitted_item_identity_after_reorder_and_removal() {
        val submitted =
            GeneralStoryForm(
                supporting =
                    listOf(
                        GeneralCharacter(id = "first", name = "첫째", description = "첫 소개"),
                        GeneralCharacter(id = "second", name = "둘째", description = "둘 소개"),
                    ),
            )
        val target = requireNotNull(generalModerationTarget("characters[1].description", submitted))
        assertEquals("second", target.itemId)
        val reordered = submitted.copy(supporting = submitted.supporting.reversed())
        assertEquals(generalFieldValue(submitted, target), generalFieldValue(reordered, target))
        val removed = submitted.copy(supporting = submitted.supporting.take(1))
        assertNull(generalFieldValue(removed, target))
        assertNotEquals(generalFieldValue(submitted, target), generalFieldValue(removed, target))
    }

    @Test
    fun setting_review_is_tab_only_and_ignores_description_only_changes() {
        val submitted = GeneralStoryForm(supporting = listOf(GeneralCharacter(name = "인물", feature = "특징")))
        val target = requireNotNull(generalModerationTarget("storySettings.characterSetting", submitted))
        assertEquals(GeneralField.ITEMS, target.field)
        val changed = submitted.copy(supporting = submitted.supporting.map { it.copy(description = "새 소개") })
        assertEquals(generalFieldValue(submitted, target), generalFieldValue(changed, target))
        assertNull(generalModerationTarget("unknown.path", submitted))
    }
}
