package app.manyak.analytics.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventContractTest {
    @Test
    fun `feature values retain the existing event payloads`() {
        assertEvent(
            AnalyticsEvent.TagCategorySelected("GENRE", "PROTAGONIST", forward = true),
            "client_storyCreate_tagCategory_selected",
            mapOf("from_category" to "GENRE", "to_category" to "PROTAGONIST", "direction" to "forward"),
        )
        assertEvent(
            AnalyticsEvent.TagCategorySelected("PROTAGONIST", "GENRE", forward = false),
            "client_storyCreate_tagCategory_selected",
            mapOf("from_category" to "PROTAGONIST", "to_category" to "GENRE", "direction" to "backward"),
        )
        assertEvent(
            AnalyticsEvent.AddTagSubmitted("GENRE"),
            "client_storyCreate_addTag_submitted",
            mapOf("category" to "GENRE"),
        )
        assertEvent(
            AnalyticsEvent.StorylineRatingClicked(7, "GOOD", active = true),
            "client_storyCreate_storylineRating_clicked",
            mapOf("storyline_id" to "7", "rating" to "GOOD", "active" to true),
        )
        assertEvent(
            AnalyticsEvent.ChatInputModeSelected("chat-fixture", "block"),
            "client_chat_inputMode_selected",
            mapOf("chat_id" to "chat-fixture", "mode" to "block"),
        )
        assertEvent(
            AnalyticsEvent.ReportSubmitted("story-fixture", "SPAM", hasDetail = false),
            "client_report_submitted",
            mapOf("target_type" to "story", "target_id" to "story-fixture", "reason" to "SPAM", "has_detail" to false),
        )
    }

    @Test
    fun `character image events contain only their screen identifier`() {
        assertEvent(
            AnalyticsEvent.StoryDetailCharacterImageClicked("story-fixture"),
            "client_storyDetail_characterImage_clicked",
            mapOf("story_id" to "story-fixture"),
        )
        assertEvent(
            AnalyticsEvent.ChatCharacterImageClicked("chat-fixture"),
            "client_chat_characterImage_clicked",
            mapOf("chat_id" to "chat-fixture"),
        )
    }

    @Test
    fun `chat tour events use the web names and zero-based step numbers`() {
        assertEvent(
            AnalyticsEvent.ChatTourShown("chat-fixture"),
            "client_chat_tour_shown",
            mapOf(
                "chat_id" to "chat-fixture",
            ),
        )
        assertEvent(
            AnalyticsEvent.ChatTourStepViewed("chat-fixture", stepNumber = 0, stepId = "add-blocks"),
            "client_chat_tourStep_viewed",
            mapOf("chat_id" to "chat-fixture", "step_number" to 0, "step_id" to "add-blocks"),
        )
        assertEvent(
            AnalyticsEvent.ChatTourCompleted("chat-fixture"),
            "client_chat_tour_completed",
            mapOf("chat_id" to "chat-fixture"),
        )
        assertEvent(
            AnalyticsEvent.ChatTourSkipButtonClicked("chat-fixture", stepNumber = 1),
            "client_chat_tourSkipButton_clicked",
            mapOf("chat_id" to "chat-fixture", "step_number" to 1),
        )
    }

    private fun assertEvent(
        event: AnalyticsEvent,
        name: String,
        properties: Map<String, Any?>,
    ) {
        assertEquals(name, event.name)
        assertEquals(properties, event.properties)
        assertEquals(name.split('_')[1], event.screenName)
    }
}
