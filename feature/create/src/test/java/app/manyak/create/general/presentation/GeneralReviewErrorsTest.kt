package app.manyak.create.general.presentation

import app.manyak.create.general.entity.GeneralModerationIssue
import app.manyak.create.general.entity.GeneralSubmission
import app.manyak.create.general.entity.GeneralTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralReviewErrorsTest {
    @Test
    fun multiple_fields_in_one_tab_and_unknown_path_fallback_are_all_preserved() {
        val review =
            GeneralSubmission(
                "id",
                "REJECTED",
                issues =
                    listOf(
                        issue("title", "제목 사유"),
                        issue("oneLineIntro", "소개 사유"),
                        issue("unknown.path", ""),
                        issue("storySettings.characterSetting", "인물 설정 사유"),
                    ),
            )
        val errors = generalReviewErrors(review, validGeneralForm())
        assertEquals(3, errors.fields.size)
        assertEquals(2, errors.notices.size)
        assertEquals(
            GeneralEditorMessage.UNKNOWN_ISSUE,
            errors.notices
                .first()
                .message.message,
        )
        assertEquals(GeneralTab.SUPPORTING, errors.notices.last().tab)
        val state = GeneralEditorState(serverErrors = errors.fields, generalNotices = errors.notices)
        val changedTarget = requireNotNull(errors.notices.last().target)
        val next =
            reduceGeneralEditor(
                state,
                GeneralEditorEvent.Form(state.form, emptyList(), setOf(changedTarget)),
            )
        assertEquals(1, next.generalNotices.size)
        assertTrue(next.generalNotices.single().tab == null)
    }

    private fun issue(
        path: String,
        reason: String,
    ) = GeneralModerationIssue(path, "text", "rule", reason)
}
