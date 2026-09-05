package app.manyak.core.analytics

import app.manyak.common.domain.chat.ChatInputMode
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.story.StoryReportReason
import app.manyak.common.entity.story.StoryTagCategory
import app.manyak.common.entity.story.StorylineRating

/**
 * 이벤트 카탈로그. 이름과 프로퍼티는 웹과 같은 공통 계약을 쓰고, 값을 이름에 넣지 않는다.
 *
 * `screen_name` 은 이름의 두 번째 토큰이다 — 웹 `deriveScreenName` 과 같은 규칙이라 대시보드가
 * 플랫폼을 가르지 않고 같은 필터를 쓴다.
 */
sealed class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any?> = emptyMap(),
) {
    val screenName: String get() = name.split('_').getOrElse(1) { name }

    // login
    data object LoginViewed : AnalyticsEvent("client_login_viewed")

    data class LoginProviderButtonClicked(
        val provider: AuthProvider,
    ) : AnalyticsEvent("client_login_${provider.wireName}Button_clicked")

    data class LoginOauthErrorShown(
        val errorCode: String,
        val provider: AuthProvider,
    ) : AnalyticsEvent(
            "client_login_oauthError_shown",
            mapOf("error_code" to errorCode, "provider" to provider.wireName),
        )

    // storyList — 홈 탭(original)·스튜디오 탭(created)
    data class StoryListViewed(
        val section: StoryListSection,
    ) : AnalyticsEvent("client_storyList_viewed", mapOf("section" to section.wire))

    data class StoryListCreateButtonClicked(
        val source: CreateButtonSource,
    ) : AnalyticsEvent("client_storyList_createButton_clicked", mapOf("source" to source.wire))

    data class StoryCardClicked(
        val storyId: String,
        val position: Int?,
        val section: StoryListSection,
    ) : AnalyticsEvent(
            "client_storyList_storyCard_clicked",
            mapOf("story_id" to storyId, "position" to position, "section" to section.wire),
        )

    data class StoryCardImpressed(
        val storyId: String,
        val position: Int?,
        val section: StoryListSection,
    ) : AnalyticsEvent(
            "client_storyList_storyCard_impressed",
            mapOf("story_id" to storyId, "position" to position, "section" to section.wire),
        )

    data class StoryOptionsOpened(
        val storyId: String,
    ) : AnalyticsEvent("client_storyList_storyOptions_opened", mapOf("story_id" to storyId))

    data class StoryListStoryDeleted(
        val storyId: String,
    ) : AnalyticsEvent("client_storyList_story_deleted", mapOf("story_id" to storyId))

    data class StoryListLoadErrorShown(
        val section: StoryListSection,
    ) : AnalyticsEvent("client_storyList_loadError_shown", mapOf("section" to section.wire))

    // storyCreate
    data object StoryCreateViewed : AnalyticsEvent("client_storyCreate_viewed")

    data class StoryCreateStepViewed(
        val step: CreateStep,
    ) : AnalyticsEvent(
            "client_storyCreate_step_viewed",
            mapOf("step_name" to step.stepName, "step_number" to step.number),
        )

    data class TagCategorySelected(
        val from: StoryTagCategory,
        val to: StoryTagCategory,
    ) : AnalyticsEvent(
            "client_storyCreate_tagCategory_selected",
            mapOf(
                "from_category" to from.name,
                "to_category" to to.name,
                "direction" to if (to.ordinal > from.ordinal) "forward" else "backward",
            ),
        )

    data class AddTagSubmitted(
        val category: StoryTagCategory,
    ) : AnalyticsEvent("client_storyCreate_addTag_submitted", mapOf("category" to category.name))

    data object StoryGenerationRequested : AnalyticsEvent("client_storyCreate_storyGeneration_requested")

    data class StorylineOptionSelected(
        val creationId: String,
        val position: Int,
    ) : AnalyticsEvent(
            "client_storyCreate_storylineOption_selected",
            mapOf("creation_id" to creationId, "position" to position),
        )

    data class SelectedTagsButtonClicked(
        val creationId: String,
    ) : AnalyticsEvent("client_storyCreate_selectedTagsButton_clicked", mapOf("creation_id" to creationId))

    data class RegenerateStorylineButtonClicked(
        val creationId: String,
    ) : AnalyticsEvent("client_storyCreate_regenerateButton_clicked", mapOf("creation_id" to creationId))

    data class StorylineTabSelected(
        val creationId: String,
        val position: Int,
    ) : AnalyticsEvent(
            "client_storyCreate_storylineTab_selected",
            mapOf("creation_id" to creationId, "position" to position),
        )

    data class StorylineRatingClicked(
        val storylineId: Long,
        val rating: StorylineRating,
        val active: Boolean,
    ) : AnalyticsEvent(
            "client_storyCreate_storylineRating_clicked",
            mapOf("storyline_id" to storylineId.toString(), "rating" to rating.name, "active" to active),
        )

    data object BackToStorylineButtonClicked : AnalyticsEvent("client_storyCreate_backToStorylineButton_clicked")

    data class RecommendedInfoClicked(
        val selected: Boolean,
    ) : AnalyticsEvent("client_storyCreate_recommendedInfo_clicked", mapOf("selected" to selected))

    data object AdditionalInfoAddButtonClicked : AnalyticsEvent("client_storyCreate_additionalInfoAddButton_clicked")

    data object AdditionalInfoRemoveButtonClicked :
        AnalyticsEvent("client_storyCreate_additionalInfoRemoveButton_clicked")

    data class StoryCompletionRequested(
        val creationId: String,
    ) : AnalyticsEvent("client_storyCreate_storyCompletion_requested", mapOf("creation_id" to creationId))

    data class CompleteErrorShown(
        val stage: CompletionStage,
    ) : AnalyticsEvent("client_storyCreate_completeError_shown", mapOf("stage" to stage.wire))

    data class DraftSaved(
        val step: CreateStep,
    ) : AnalyticsEvent("client_storyCreate_draftSaved", mapOf("step" to step.draftName))

    data object ResumeDialogShown : AnalyticsEvent("client_storyCreate_resumeDialog_shown")

    data object ResumeDialogContinued : AnalyticsEvent("client_storyCreate_resumeDialog_continued")

    data object ResumeDialogDiscarded : AnalyticsEvent("client_storyCreate_resumeDialog_discarded")

    data class ContinueBannerShown(
        val stage: PendingCreationStage,
    ) : AnalyticsEvent("client_storyCreate_continueBanner_shown", mapOf("stage" to stage.name))

    data class ContinueBannerClicked(
        val stage: PendingCreationStage,
    ) : AnalyticsEvent("client_storyCreate_continueBanner_clicked", mapOf("stage" to stage.name))

    data class CreateExitButtonClicked(
        val step: CreateStep,
    ) : AnalyticsEvent(
            "client_storyCreate_exitButton_clicked",
            mapOf("step_name" to step.stepName, "step_number" to step.number),
        )

    data class StoryCreateCompleted(
        val storyId: String,
        val chatId: String,
    ) : AnalyticsEvent("client_storyCreate_completed", mapOf("story_id" to storyId, "chat_id" to chatId))

    // storyDetail
    data class StoryDetailViewed(
        val storyId: String,
    ) : AnalyticsEvent("client_storyDetail_viewed", mapOf("story_id" to storyId))

    data class ChatStartButtonClicked(
        val storyId: String,
    ) : AnalyticsEvent("client_storyDetail_chatStartButton_clicked", mapOf("story_id" to storyId))

    data class ThumbnailClicked(
        val storyId: String,
    ) : AnalyticsEvent("client_storyDetail_thumbnail_clicked", mapOf("story_id" to storyId))

    data class StartSettingSelected(
        val storyId: String,
        val startSettingId: String,
    ) : AnalyticsEvent(
            "client_storyDetail_startSetting_selected",
            mapOf("story_id" to storyId, "start_setting_id" to startSettingId),
        )

    data class StoryDetailStoryDeleted(
        val storyId: String,
    ) : AnalyticsEvent("client_storyDetail_story_deleted", mapOf("story_id" to storyId))

    // chatList
    data object ChatListViewed : AnalyticsEvent("client_chatList_viewed")

    data class ChatCardClicked(
        val chatId: String,
        val position: Int?,
    ) : AnalyticsEvent("client_chatList_chatCard_clicked", mapOf("chat_id" to chatId, "position" to position))

    data class ChatCardImpressed(
        val chatId: String,
        val position: Int?,
    ) : AnalyticsEvent("client_chatList_chatCard_impressed", mapOf("chat_id" to chatId, "position" to position))

    data class ChatOptionsOpened(
        val chatId: String,
    ) : AnalyticsEvent("client_chatList_chatOptions_opened", mapOf("chat_id" to chatId))

    data class ChatListChatDeleted(
        val chatId: String,
    ) : AnalyticsEvent("client_chatList_chat_deleted", mapOf("chat_id" to chatId))

    data object ChatListLoadErrorShown : AnalyticsEvent("client_chatList_loadError_shown")

    // chat
    data class ChatViewed(
        val chatId: String,
    ) : AnalyticsEvent("client_chat_viewed", mapOf("chat_id" to chatId))

    data class ChatInputModeSelected(
        val chatId: String,
        val mode: ChatInputMode,
    ) : AnalyticsEvent("client_chat_inputMode_selected", mapOf("chat_id" to chatId, "mode" to mode.wire))

    data class ChoicesToggleClicked(
        val chatId: String,
        val enabled: Boolean,
    ) : AnalyticsEvent("client_chat_choicesToggle_clicked", mapOf("chat_id" to chatId, "enabled" to enabled))

    data class MessageInputSubmitted(
        val chatId: String,
        val turnNumber: Int,
        val inputMode: MessageInputMode,
    ) : AnalyticsEvent(
            "client_chat_messageInput_submitted",
            mapOf("chat_id" to chatId, "turn_number" to turnNumber, "input_mode" to inputMode.wire),
        )

    data class AddBlockButtonClicked(
        val chatId: String,
        val blockType: String,
    ) : AnalyticsEvent("client_chat_addBlockButton_clicked", mapOf("chat_id" to chatId, "block_type" to blockType))

    data class RemoveBlockButtonClicked(
        val chatId: String,
        val blockType: String,
    ) : AnalyticsEvent(
            "client_chat_removeBlockButton_clicked",
            mapOf("chat_id" to chatId, "block_type" to blockType),
        )

    data class ChoiceOptionSelected(
        val chatId: String,
        val turnNumber: Int,
        val position: Int,
    ) : AnalyticsEvent(
            "client_chat_choiceOption_selected",
            mapOf("chat_id" to chatId, "turn_number" to turnNumber, "position" to position),
        )

    data class ChoiceFillButtonClicked(
        val chatId: String,
        val turnNumber: Int,
        val position: Int,
    ) : AnalyticsEvent(
            "client_chat_choiceFillButton_clicked",
            mapOf("chat_id" to chatId, "turn_number" to turnNumber, "position" to position),
        )

    data class RegenerateTurnButtonClicked(
        val chatId: String,
        val turnNumber: Int,
    ) : AnalyticsEvent("client_chat_regenerateButton_clicked", mapOf("chat_id" to chatId, "turn_number" to turnNumber))

    data class StreamErrorShown(
        val chatId: String,
        val turnNumber: Int,
    ) : AnalyticsEvent("client_chat_streamError_shown", mapOf("chat_id" to chatId, "turn_number" to turnNumber))

    data class ChatLoadErrorShown(
        val chatId: String,
    ) : AnalyticsEvent("client_chat_loadError_shown", mapOf("chat_id" to chatId))

    data class ChatRetryButtonClicked(
        val chatId: String,
    ) : AnalyticsEvent("client_chat_retryButton_clicked", mapOf("chat_id" to chatId))

    // creditShortageDialog — 이름은 웹 호환, 실제 UI 는 토스트
    data class CreditShortageShown(
        val trigger: CreditShortageTrigger,
    ) : AnalyticsEvent("client_creditShortageDialog_shown", mapOf("trigger" to trigger.wire))

    // report — 네 화면이 시트를 공유하므로 source 로 가른다
    data class ReportSheetOpened(
        val storyId: String,
        val source: ReportSource,
    ) : AnalyticsEvent(
            "client_report_sheet_opened",
            mapOf("target_type" to "story", "target_id" to storyId, "source" to source.wire),
        )

    data class ReportSubmitted(
        val storyId: String,
        val reason: StoryReportReason,
        val hasDetail: Boolean,
    ) : AnalyticsEvent(
            "client_report_submitted",
            mapOf(
                "target_type" to "story",
                "target_id" to storyId,
                "reason" to reason.name,
                "has_detail" to hasDetail,
            ),
        )

    data class ReportFailed(
        val errorType: String,
    ) : AnalyticsEvent("client_report_failed", mapOf("target_type" to "story", "error_type" to errorType))

    // account
    data object AccountViewed : AnalyticsEvent("client_account_viewed")

    data object LogoutButtonClicked : AnalyticsEvent("client_account_logoutButton_clicked")

    data class LinkAccountButtonClicked(
        val provider: AuthProvider,
    ) : AnalyticsEvent("client_account_linkAccountButton_clicked", mapOf("provider" to provider.wireName))

    data object CreditChargeButtonClicked : AnalyticsEvent("client_account_creditChargeButton_clicked")

    // creditCharge
    data object CreditChargeViewed : AnalyticsEvent("client_creditCharge_viewed")

    data object AttendanceButtonClicked : AnalyticsEvent("client_creditCharge_attendanceButton_clicked")

    data class CreditChargeTabSelected(
        val tab: String,
    ) : AnalyticsEvent("client_creditCharge_tab_selected", mapOf("tab" to tab))

    // withdrawal
    data object WithdrawalViewed : AnalyticsEvent("client_withdrawal_viewed")

    data object WithdrawalCompleted : AnalyticsEvent("client_withdrawal_completed")

    // invite
    data object InviteViewed : AnalyticsEvent("client_invite_viewed")

    data object InviteCopyButtonClicked : AnalyticsEvent("client_invite_copyButton_clicked")

    data object InviteShareButtonClicked : AnalyticsEvent("client_invite_shareButton_clicked")

    data class InviteCodeSubmitted(
        val source: InviteCodeSource,
    ) : AnalyticsEvent("client_invite_codeInput_submitted", mapOf("source" to source.wire))

    data class InviteCodeSucceeded(
        val source: InviteCodeSource,
    ) : AnalyticsEvent("client_invite_codeInput_succeeded", mapOf("source" to source.wire))

    data class InviteCodeFailed(
        val source: InviteCodeSource,
        val errorType: String,
    ) : AnalyticsEvent("client_invite_codeInput_failed", mapOf("source" to source.wire, "error_type" to errorType))

    data object InviteOnboardingShown : AnalyticsEvent("client_inviteOnboarding_shown")

    data object InviteOnboardingSkipped : AnalyticsEvent("client_inviteOnboarding_skipped")

    // feedback
    data object FeedbackViewed : AnalyticsEvent("client_feedback_viewed")

    data object FeedbackFormSubmitted : AnalyticsEvent("client_feedback_form_submitted")

    // legal — 앱은 화면 하나지만 screen_name 은 문서별로 웹과 맞춘다
    data object TermsViewed : AnalyticsEvent("client_terms_viewed")

    data object PrivacyViewed : AnalyticsEvent("client_privacy_viewed")

    data object ServiceInfoViewed : AnalyticsEvent("client_serviceInfo_viewed")
}

enum class StoryListSection(
    val wire: String,
) {
    ORIGINAL("original"),
    CREATED("created"),
}

enum class CreateButtonSource(
    val wire: String,
) {
    FAB("fab"),
    EMPTY_STATE("emptyState"),
}

/** 제작 퍼널 단계. 앱은 완료 단계가 화면이 아니라 `complete` 가 없다. */
enum class CreateStep(
    val stepName: String,
    val draftName: String,
) {
    KEYWORD("keyword", "keyword"),
    STORYLINE_SELECT("storylineSelect", "storyline-select"),
    ADDITIONAL_INFO("additionalInfo", "additional-info"),
    ;

    /** 선언 순서가 곧 퍼널 순서다. 웹과 같이 1부터 센다. */
    val number: Int get() = ordinal + 1
}

enum class CompletionStage(
    val wire: String,
) {
    STORY("story"),
    CHAT("chat"),
}

enum class PendingCreationStage {
    KEYWORD_DRAFT,
    STORYLINE_GENERATION,
    STORY_COMPLETION,
    STORY_DRAFT,
}

enum class MessageInputMode(
    val wire: String,
) {
    BLOCK("block"),
    PLAIN("plain"),
    CHOICE("choice"),
}

enum class CreditShortageTrigger(
    val wire: String,
) {
    STORY_CREATE("story_create"),
    CHAT_TURN("chat_turn"),
}

enum class ReportSource(
    val wire: String,
) {
    STORY_DETAIL("storyDetail"),
    STUDIO("studio"),
    CHAT_LIST("chatList"),
    CHAT("chat"),
}

enum class InviteCodeSource(
    val wire: String,
) {
    INVITE_PAGE("invite_page"),
    ONBOARDING("onboarding"),
}

val ChatInputMode.wire: String
    get() =
        when (this) {
            ChatInputMode.BLOCK -> "block"
            ChatInputMode.PLAIN -> "plain"
        }
