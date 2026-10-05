package app.manyak.create.general.presentation

import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralSubmission

sealed interface GeneralEditorEntry {
    data class Draft(
        val draftId: String,
    ) : GeneralEditorEntry

    data class Submission(
        val submissionId: String,
    ) : GeneralEditorEntry

    data class Edit(
        val storyId: String,
    ) : GeneralEditorEntry
}

data class GeneralEditorState(
    val form: GeneralStoryForm = GeneralStoryForm(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val submitting: Boolean = false,
    val saving: Boolean = false,
    val saveLocked: Boolean = false,
    val submission: GeneralSubmission? = null,
    val submittedForm: GeneralStoryForm? = null,
    val savedForm: GeneralStoryForm? = null,
    val errors: List<GeneralFieldError> = emptyList(),
    val serverErrors: Map<GeneralFieldTarget, GeneralReviewMessage> = emptyMap(),
    val generalIssues: List<String> = emptyList(),
    val generalNotices: List<GeneralReviewNotice> = emptyList(),
    val uploading: Set<GeneralFieldTarget> = emptySet(),
    val genres: List<String> = emptyList(),
    val genresFailed: Boolean = false,
    val featuredGenres: List<String> = emptyList(),
    val genreSearch: GeneralGenreSearchState = GeneralGenreSearchState(),
    val validationRequest: Int = 0,
    val showExit: Boolean = false,
    val uncertainSubmission: Boolean = false,
    val acceptanceRecordFailed: Boolean = false,
) {
    val reviewing: Boolean get() = submission?.status == "PENDING"
    val inputsEnabled: Boolean get() =
        !loading &&
            !loadFailed &&
            !submitting &&
            !reviewing &&
            !uncertainSubmission &&
            !acceptanceRecordFailed
}

data class GeneralReviewMessage(
    val text: String? = null,
    val message: GeneralEditorMessage? = null,
)

enum class GeneralEditorMessage {
    LOAD_FAILED,
    REGISTER_FAILED,
    UPDATE_FAILED,
    UPDATED,
    REVIEWING,
    PENDING,
    REJECTED,
    TIMEOUT_CREATE,
    TIMEOUT_EDIT,
    IMAGES_TOO_LARGE,
    UPLOAD_NOT_FOUND,
    UPLOAD_FAILED,
    UNKNOWN_ISSUE,
    IMAGE_INVALID,
    IMAGE_UNREADABLE,
    IMAGE_ERROR,
    SUBMIT_UNKNOWN,
    DRAFT_FAILED,
    CHAT_FAILED,
}

sealed interface GeneralEditorIntent {
    data class ChangeForm(
        val form: GeneralStoryForm,
    ) : GeneralEditorIntent

    data class Blur(
        val target: GeneralFieldTarget,
    ) : GeneralEditorIntent

    data class Upload(
        val target: GeneralFieldTarget,
        val path: String,
    ) : GeneralEditorIntent

    data object Submit : GeneralEditorIntent

    data class SaveDraft(
        val showToast: Boolean = true,
    ) : GeneralEditorIntent

    data object Retry : GeneralEditorIntent

    data class SearchGenres(
        val query: String,
    ) : GeneralEditorIntent

    data class ExpandGenres(
        val expanded: Boolean,
    ) : GeneralEditorIntent

    data object RetryGenres : GeneralEditorIntent

    data object Close : GeneralEditorIntent

    data object ConfirmClose : GeneralEditorIntent

    data object DismissClose : GeneralEditorIntent
}

sealed interface GeneralEditorEffect {
    data class Message(
        val message: GeneralEditorMessage,
    ) : GeneralEditorEffect

    data object Close : GeneralEditorEffect

    data class Approved(
        val storyId: String,
        val chatId: String? = null,
    ) : GeneralEditorEffect
}

sealed interface GeneralEditorEvent {
    data class Loaded(
        val form: GeneralStoryForm,
        val savedForm: GeneralStoryForm? = null,
        val submission: GeneralSubmission? = null,
    ) : GeneralEditorEvent

    data object LoadFailed : GeneralEditorEvent

    data class State(
        val value: GeneralEditorState,
    ) : GeneralEditorEvent

    data class Form(
        val value: GeneralStoryForm,
        val errors: List<GeneralFieldError>,
        val changed: Set<GeneralFieldTarget>,
    ) : GeneralEditorEvent

    data class Errors(
        val errors: List<GeneralFieldError>,
        val reveal: Boolean,
    ) : GeneralEditorEvent

    data class Submission(
        val submission: GeneralSubmission,
        val form: GeneralStoryForm,
    ) : GeneralEditorEvent

    data class Review(
        val submission: GeneralSubmission,
        val errors: Map<GeneralFieldTarget, GeneralReviewMessage>,
        val issues: List<String>,
        val notices: List<GeneralReviewNotice> = emptyList(),
    ) : GeneralEditorEvent

    data class Busy(
        val submitting: Boolean,
    ) : GeneralEditorEvent

    data class Saving(
        val saving: Boolean,
        val locked: Boolean = false,
        val savedForm: GeneralStoryForm? = null,
    ) : GeneralEditorEvent

    data class Genres(
        val genres: List<String>,
        val failed: Boolean,
        val featuredGenres: List<String> = emptyList(),
    ) : GeneralEditorEvent

    data class GenreSearch(
        val state: GeneralGenreSearchState,
    ) : GeneralEditorEvent

    data class Exit(
        val visible: Boolean,
    ) : GeneralEditorEvent

    data class Uploading(
        val target: GeneralFieldTarget,
        val uploading: Boolean,
    ) : GeneralEditorEvent

    data class Uncertain(
        val value: Boolean,
    ) : GeneralEditorEvent

    data class AcceptanceRecord(
        val failed: Boolean,
    ) : GeneralEditorEvent
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun reduceGeneralEditor(
    state: GeneralEditorState,
    event: GeneralEditorEvent,
): GeneralEditorState =
    when (event) {
        is GeneralEditorEvent.Loaded ->
            state.copy(
                form = event.form,
                savedForm = event.savedForm,
                submission = event.submission,
                submittedForm = event.form.takeIf { event.submission != null },
                loading = false,
                loadFailed = false,
            )
        GeneralEditorEvent.LoadFailed -> state.copy(loading = false, loadFailed = true)
        is GeneralEditorEvent.State -> event.value
        is GeneralEditorEvent.Form ->
            state.copy(
                form = event.value,
                errors = event.errors,
                serverErrors = state.serverErrors - event.changed,
                generalNotices = state.generalNotices.filterNot { it.target in event.changed },
            )
        is GeneralEditorEvent.Errors ->
            state.copy(
                errors = event.errors,
                validationRequest = state.validationRequest + if (event.reveal) 1 else 0,
            )
        is GeneralEditorEvent.Submission ->
            state.copy(
                submission = event.submission,
                submittedForm = event.form,
                submitting = false,
                serverErrors = emptyMap(),
                generalIssues = emptyList(),
                generalNotices = emptyList(),
            )
        is GeneralEditorEvent.Review ->
            state.copy(
                submission = event.submission,
                submitting = false,
                serverErrors = event.errors,
                generalIssues = event.issues,
                generalNotices = event.notices,
                validationRequest = state.validationRequest + 1,
            )
        is GeneralEditorEvent.Busy -> state.copy(submitting = event.submitting)
        is GeneralEditorEvent.Saving ->
            state.copy(
                saving = event.saving,
                saveLocked = event.locked,
                savedForm = event.savedForm ?: state.savedForm,
            )
        is GeneralEditorEvent.Genres ->
            state.copy(
                genres = event.genres,
                genresFailed = event.failed,
                featuredGenres = event.featuredGenres,
            )
        is GeneralEditorEvent.GenreSearch -> state.copy(genreSearch = event.state)
        is GeneralEditorEvent.Exit -> state.copy(showExit = event.visible)
        is GeneralEditorEvent.Uploading ->
            state.copy(
                uploading = if (event.uploading) state.uploading + event.target else state.uploading - event.target,
            )
        is GeneralEditorEvent.Uncertain -> state.copy(uncertainSubmission = event.value, submitting = false)
        is GeneralEditorEvent.AcceptanceRecord -> state.copy(acceptanceRecordFailed = event.failed)
    }
