package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralErrorReason
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStoryValidationTest {
    @Test
    fun a_valid_form_accepts_optional_empty_fields() {
        assertTrue(validateGeneralStoryForm(validForm()).isEmpty())
    }

    @Test
    fun duplicate_names_take_priority_over_minimum_length_and_only_trim_edges() {
        val form =
            validForm().copy(
                protagonist = GeneralCharacter(name = "가", gender = GeneralGender.MALE, feature = "특징"),
                supporting =
                    listOf(
                        GeneralCharacter(id = "duplicate", name = " 가 ", gender = GeneralGender.FEMALE),
                        GeneralCharacter(id = "spaced", name = "가 나", gender = GeneralGender.FEMALE),
                        GeneralCharacter(id = "unspaced", name = "가나", gender = GeneralGender.FEMALE),
                    ),
            )
        val errors = validateGeneralStoryForm(form)

        assertEquals(GeneralErrorReason.DUPLICATE, errors.single { it.target.itemId == "duplicate" }.reason)
        assertFalse(errors.any { it.target.itemId in listOf("spaced", "unspaced") })
    }

    @Test
    fun duplicate_endings_are_scoped_to_start_setting() {
        val ending = GeneralEnding(name = "같은 엔딩", minTurns = "10", condition = "달성 조건", epilogue = "마무리")
        val start = validForm().startSettings.single().copy(endings = listOf(ending))
        val form = validForm().copy(startSettings = listOf(start, start.copy(id = "other")))

        assertTrue(validateGeneralStoryForm(form).isEmpty())
        val duplicated =
            form.copy(startSettings = listOf(start.copy(endings = listOf(ending, ending.copy(id = "second")))))
        assertEquals("second", validateGeneralStoryForm(duplicated).single().target.itemId)
    }

    @Test
    fun ending_min_turns_is_required_and_between_zero_and_fifty() {
        fun reasonFor(minTurns: String): GeneralErrorReason? {
            val ending = GeneralEnding(name = "엔딩", minTurns = minTurns, condition = "달성 조건", epilogue = "마무리")
            val start = validForm().startSettings.single().copy(endings = listOf(ending))
            return validateGeneralStoryForm(validForm().copy(startSettings = listOf(start)))
                .singleOrNull { it.target.field == GeneralField.MIN_TURNS }
                ?.reason
        }

        assertEquals(GeneralErrorReason.REQUIRED, reasonFor(""))
        assertEquals(null, reasonFor("0"))
        assertEquals(null, reasonFor("50"))
        assertEquals(GeneralErrorReason.INVALID_NUMBER, reasonFor("51"))
    }

    @Test
    fun validation_points_to_first_error_in_tab_order_with_stable_item_ids() {
        val form =
            validForm().copy(
                title = "",
                startSettings = listOf(GeneralStartSetting(id = "start", name = "이름")),
                mainEvents = listOf(GeneralMainEvent(id = "event", name = "사건")),
            )
        val errors = validateGeneralStoryForm(form)

        assertEquals(GeneralTab.PROFILE, errors.first().target.tab)
        assertEquals(GeneralField.TITLE, errors.first().target.field)
        assertTrue(errors.any { it.target.startId == "start" && it.target.field == GeneralField.SUGGESTED_INPUT })
        assertTrue(errors.any { it.target.itemId == "event" && it.target.field == GeneralField.KEY_SENTENCE })
    }

    @Test
    fun limits_are_checked_for_restored_data_and_supplied_genres() {
        val form =
            validForm().copy(
                title = "가".repeat(101),
                genres = listOf("판타지", "사라진 장르"),
                supporting = listOf(GeneralCharacter(name = "동료", gender = GeneralGender.MALE, description = "가")),
            )
        val errors = validateGeneralStoryForm(form, allowedGenres = setOf("판타지"))

        assertEquals(GeneralErrorReason.TOO_LONG, errors.first().reason)
        assertTrue(
            errors.any {
                it.target.field == GeneralField.CHARACTER_DESCRIPTION && it.reason == GeneralErrorReason.TOO_SHORT
            },
        )
        assertTrue(errors.any { it.reason == GeneralErrorReason.INVALID_GENRE })
    }

    private fun validForm() =
        GeneralStoryForm(
            title = "스토리 제목",
            oneLineIntro = "한 줄 소개",
            world = "세계관",
            progression = "전개 방식",
            protagonist = GeneralCharacter(name = "주인공", gender = GeneralGender.FEMALE, feature = "특징"),
            supporting = listOf(GeneralCharacter(name = "동료", gender = GeneralGender.MALE)),
            startSettings =
                listOf(
                    GeneralStartSetting(
                        name = "시작 상황",
                        prologue = "프롤로그",
                        situation = "상황 설명",
                        suggestedInputs = listOf("첫 입력", "두 번째", "세 번째"),
                    ),
                ),
            genres = listOf("판타지"),
        )
}
