package app.manyak.analytics.entity

import app.manyak.common.entity.story.CreateTutorialStage
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

    @Test
    fun `create tutorial events use the web names and stage values`() {
        assertEvent(
            AnalyticsEvent.CreateTutorialStepViewed(CreateTutorialStage.ADDITIONAL_INFO),
            "client_storyCreate_tutorialStep_viewed",
            mapOf("stage" to "additional-info"),
        )
        assertEvent(
            AnalyticsEvent.CreateTutorialSkipButtonClicked(CreateTutorialStage.INTRO),
            "client_storyCreate_tutorialSkipButton_clicked",
            mapOf("stage" to "intro"),
        )
        assertEvent(
            AnalyticsEvent.CreateTutorialCompleted(toOriginals = true),
            "client_storyCreate_tutorial_completed",
            mapOf("action" to "originals"),
        )
        assertEvent(
            AnalyticsEvent.CreateTutorialCompleted(toOriginals = false),
            "client_storyCreate_tutorial_completed",
            mapOf("action" to "close"),
        )
    }

    @Test
    fun `profile edit completion carries only what changed`() {
        assertEvent(AnalyticsEvent.ProfileEditViewed, "client_profileEdit_viewed", emptyMap())
        assertEvent(
            AnalyticsEvent.ProfileEditCompleted(nicknameChanged = true, imageChanged = false),
            "client_profileEdit_completed",
            mapOf("nickname_changed" to true, "image_changed" to false),
        )
    }

    @Test
    fun `chat edit and fork clicks carry the target turn number`() {
        assertEvent(
            AnalyticsEvent.ChatEditButtonClicked(chatId = "chat-1", turnNumber = 2),
            "client_chat_editButton_clicked",
            mapOf("chat_id" to "chat-1", "turn_number" to 2),
        )
        assertEvent(
            AnalyticsEvent.ChatForkButtonClicked(chatId = "chat-1", turnNumber = 1),
            "client_chat_forkButton_clicked",
            mapOf("chat_id" to "chat-1", "turn_number" to 1),
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
