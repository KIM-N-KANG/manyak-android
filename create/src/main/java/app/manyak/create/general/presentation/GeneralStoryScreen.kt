package app.manyak.create.general.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.create.R
import app.manyak.create.general.presentation.form.GeneralStoryFormContent
import app.manyak.create.general.presentation.form.labelRes
import app.manyak.create.presentation.component.SaveDraftWhenBackgrounded
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import app.manyak.designsystem.R as DesignsystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralStoryScreen(
    entry: GeneralEditorEntry,
    onClose: () -> Unit,
    onApproved: (storyId: String, chatId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeneralStoryViewModel =
        hiltViewModel<GeneralStoryViewModel, GeneralStoryViewModel.Factory>(
            creationCallback = { it.create(entry) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    val approved by rememberUpdatedState(onApproved)
    var pickerActive by rememberSaveable { mutableStateOf(false) }
    val editing = entry is GeneralEditorEntry.Edit
    val onIntent = viewModel::onIntent
    BackHandler { onIntent(GeneralEditorIntent.Close) }
    SaveDraftWhenBackgrounded { if (!pickerActive) onIntent(GeneralEditorIntent.SaveDraft(false)) }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.observeReview() }
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    GeneralEditorEffect.Close -> close()
                    is GeneralEditorEffect.Approved -> approved(effect.storyId, effect.chatId)
                    is GeneralEditorEffect.Message ->
                        Toast
                            .makeText(
                                context,
                                effect.message.resource(),
                                Toast.LENGTH_SHORT,
                            ).show()
                }
            }
        }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        GeneralEditorHeader(entry, state, onIntent)
        when {
            state.loading ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { ManyakProgressIndicator() }
            state.loadFailed ->
                LoadFailedContent(
                    message = stringResource(R.string.general_editor_load_failed),
                    onRetry = { onIntent(GeneralEditorIntent.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )
            else -> {
                ReviewBanner(state, editing) { onIntent(GeneralEditorIntent.Retry) }
                GeneralEditorForm(state, editing, onIntent, { pickerActive = it }, Modifier.weight(1f))
            }
        }
    }
    if (state.showExit) GeneralExitDialog(state, editing, onIntent)
}

@Composable
private fun GeneralEditorForm(
    state: GeneralEditorState,
    editing: Boolean,
    onIntent: (GeneralEditorIntent) -> Unit,
    onPickerActive: (Boolean) -> Unit,
    modifier: Modifier,
) {
    GeneralStoryFormContent(
        form = state.form,
        errors = state.errors,
        serverErrors =
            state.serverErrors.mapValues { (_, value) ->
                value.text
                    ?: stringResource(checkNotNull(value.message).resource())
            },
        genres = state.genres,
        genreSearch = state.genreSearch,
        featuredGenres = state.featuredGenres,
        genreCatalogFailed = state.genresFailed,
        onGenreQuery = { onIntent(GeneralEditorIntent.SearchGenres(it)) },
        onGenreExpanded = { onIntent(GeneralEditorIntent.ExpandGenres(it)) },
        onRetryGenres = { onIntent(GeneralEditorIntent.RetryGenres) },
        enabled = state.inputsEnabled,
        tabsEnabled = !state.submitting && (!state.reviewing || editing),
        submitting = state.submitting || state.reviewing,
        submitLabel =
            stringResource(
                if (editing) R.string.general_editor_save else R.string.general_editor_register,
            ),
        onFormChange = { onIntent(GeneralEditorIntent.ChangeForm(it)) },
        onSubmit = { onIntent(GeneralEditorIntent.Submit) },
        onFieldBlur = { onIntent(GeneralEditorIntent.Blur(it)) },
        validationRequest = state.validationRequest,
        onUploadImage = { target, path -> onIntent(GeneralEditorIntent.Upload(target, path)) },
        uploadingTargets = state.uploading,
        onPickerActiveChanged = onPickerActive,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralEditorHeader(
    entry: GeneralEditorEntry,
    state: GeneralEditorState,
    onIntent: (GeneralEditorIntent) -> Unit,
) {
    val editing = entry is GeneralEditorEntry.Edit
    TopAppBar(
        title = {
            Text(
                stringResource(if (editing) R.string.general_editor_edit_title else R.string.general_editor_title),
                style = ManyakTheme.typography.titleLarge,
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry is GeneralEditorEntry.Draft && state.submission == null) {
                    ManyakTextButton(
                        onClick = { onIntent(GeneralEditorIntent.SaveDraft()) },
                        enabled =
                            state.inputsEnabled && !state.saving && !state.saveLocked && state.uploading.isEmpty(),
                    ) {
                        if (state.saving) {
                            ManyakProgressIndicator()
                        } else {
                            Text(
                                stringResource(R.string.create_draft_save),
                                style = ManyakTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                ManyakIconButton(
                    iconRes = DesignsystemR.drawable.ic_close,
                    contentDescription = stringResource(R.string.create_close_funnel),
                    onClick = { onIntent(GeneralEditorIntent.Close) },
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ManyakTheme.colors.surface),
    )
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun ReviewBanner(
    state: GeneralEditorState,
    editing: Boolean,
    onRetry: () -> Unit,
) {
    val submission = state.submission
    val title =
        when {
            state.acceptanceRecordFailed -> R.string.general_editor_acceptance_failed
            state.uncertainSubmission -> R.string.general_editor_submit_unknown
            submission?.status == "PENDING" && editing -> R.string.general_editor_update_pending_title
            submission?.status == "PENDING" -> R.string.general_editor_reviewing
            submission?.status == "REJECTED" -> R.string.general_editor_rejected_title
            submission?.status == "FAILED" -> R.string.general_editor_failed_title
            else -> null
        } ?: return
    val description = reviewDescription(state.submission, editing)
    Column(
        Modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            stringResource(title),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.textDanger,
        )
        description?.let {
            Text(stringResource(it), style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
        }
        state.generalNotices.forEach { notice ->
            val reason = notice.message.text ?: stringResource(checkNotNull(notice.message.message).resource())
            val text =
                notice.tab?.let {
                    stringResource(
                        R.string.general_editor_tab_reason,
                        stringResource(it.labelRes()),
                        reason,
                    )
                }
                    ?: reason
            Text(text, style = ManyakTheme.typography.bodySmall, color = ManyakTheme.colors.textDanger)
        }
        if (state.acceptanceRecordFailed) {
            ManyakTextButton(onClick = onRetry) {
                Text(stringResource(DesignsystemR.string.common_retry), style = ManyakTheme.typography.labelLarge)
            }
        }
    }
}

private fun reviewDescription(
    submission: app.manyak.create.general.entity.GeneralSubmission?,
    editing: Boolean,
): Int? =
    when (submission?.status) {
        "PENDING" -> R.string.general_editor_update_pending_description.takeIf { editing }
        "REJECTED" -> if (editing) R.string.general_editor_rejected_edit else R.string.general_editor_rejected_create
        "FAILED" ->
            when {
                submission.imageErrors.isNotEmpty() && editing -> R.string.general_editor_image_edit
                submission.imageErrors.isNotEmpty() -> R.string.general_editor_image_create
                editing -> R.string.general_editor_failed_edit
                else -> R.string.general_editor_failed_create
            }
        else -> null
    }

@Composable
private fun GeneralExitDialog(
    state: GeneralEditorState,
    editing: Boolean,
    onIntent: (GeneralEditorIntent) -> Unit,
) {
    val changedAfterSubmission = state.submittedForm?.let { it != state.form } == true
    val title =
        if (editing) {
            R.string.general_editor_edit_exit_title
        } else if (state.submission == null &&
            state.form != state.savedForm
        ) {
            R.string.create_unsaved_warning_title
        } else {
            R.string.general_editor_exit_title
        }
    val description =
        when {
            editing -> R.string.general_editor_edit_exit_description
            changedAfterSubmission -> R.string.general_editor_exit_edited_submission
            state.submission != null -> R.string.general_editor_exit_submitted
            state.savedForm == null -> R.string.create_unsaved_input_warning_description
            state.form != state.savedForm -> R.string.create_unsaved_warning_description
            else -> R.string.create_exit_warning_description
        }
    ManyakDestructiveDialog(
        title = stringResource(title),
        description = stringResource(description),
        confirmLabel = stringResource(R.string.general_editor_exit_leave),
        cancelLabel = stringResource(R.string.general_editor_exit_close),
        onConfirm = { onIntent(GeneralEditorIntent.ConfirmClose) },
        onDismiss = { onIntent(GeneralEditorIntent.DismissClose) },
    )
}

@Suppress("CyclomaticComplexMethod")
@StringRes
internal fun GeneralEditorMessage.resource(): Int =
    when (this) {
        GeneralEditorMessage.LOAD_FAILED -> R.string.general_editor_load_failed
        GeneralEditorMessage.REGISTER_FAILED -> R.string.general_editor_register_failed
        GeneralEditorMessage.UPDATE_FAILED -> R.string.general_editor_update_failed
        GeneralEditorMessage.UPDATED -> R.string.general_editor_updated
        GeneralEditorMessage.REVIEWING -> R.string.general_editor_reviewing
        GeneralEditorMessage.PENDING -> R.string.general_editor_pending
        GeneralEditorMessage.REJECTED -> R.string.general_editor_rejected_toast
        GeneralEditorMessage.TIMEOUT_CREATE -> R.string.general_editor_timeout_create
        GeneralEditorMessage.TIMEOUT_EDIT -> R.string.general_editor_timeout_edit
        GeneralEditorMessage.IMAGES_TOO_LARGE -> R.string.general_editor_images_too_large
        GeneralEditorMessage.UPLOAD_NOT_FOUND -> R.string.general_editor_upload_not_found
        GeneralEditorMessage.UPLOAD_FAILED -> R.string.general_editor_upload_failed
        GeneralEditorMessage.UNKNOWN_ISSUE -> R.string.general_editor_unknown_issue
        GeneralEditorMessage.IMAGE_INVALID -> R.string.general_editor_image_invalid
        GeneralEditorMessage.IMAGE_UNREADABLE -> R.string.general_editor_image_unreadable
        GeneralEditorMessage.IMAGE_ERROR -> R.string.general_editor_image_error
        GeneralEditorMessage.SUBMIT_UNKNOWN -> R.string.general_editor_submit_unknown
        GeneralEditorMessage.DRAFT_SAVED -> R.string.create_draft_saved
        GeneralEditorMessage.DRAFT_FAILED -> R.string.general_editor_draft_failed
        GeneralEditorMessage.CHAT_FAILED -> R.string.general_editor_chat_failed
    }
