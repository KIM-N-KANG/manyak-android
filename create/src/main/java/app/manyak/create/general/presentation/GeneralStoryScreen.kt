package app.manyak.create.general.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.create.R
import app.manyak.create.general.presentation.form.GeneralStoryFormContent
import app.manyak.create.general.presentation.form.labelRes
import app.manyak.create.presentation.component.CreateFunnelHeader
import app.manyak.create.presentation.component.SaveDraftWhenBackgrounded
import app.manyak.create.presentation.state.DraftSaveStatus
import app.manyak.create.presentation.state.DraftSaveUiState
import app.manyak.designsystem.component.FocusScrollMargin
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import app.manyak.designsystem.R as DesignsystemR

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
    FocusScrollMargin {
        Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GeneralEditorBody(entry, state, editing, onIntent) { pickerActive = it }
        }
    }
    if (state.showExit) GeneralExitDialog(state, editing, onIntent)
}

@Composable
private fun GeneralEditorBody(
    entry: GeneralEditorEntry,
    state: GeneralEditorState,
    editing: Boolean,
    onIntent: (GeneralEditorIntent) -> Unit,
    onPickerActive: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CreateFunnelHeader(
            title = stringResource(if (editing) R.string.general_editor_edit_title else R.string.general_editor_title),
            draftSave = if (editing) null else state.draftSave(entry),
            onSaveDraft = { onIntent(GeneralEditorIntent.SaveDraft()) },
            onClose = { onIntent(GeneralEditorIntent.Close) },
        )
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
                GeneralEditorForm(state, editing, onIntent, onPickerActive, Modifier.weight(1f))
            }
        }
    }
}

/** 간편 제작과 같은 헤더 임시 저장 버튼의 상태. 등록을 요청한 뒤에는 저장본을 만들지 않아 잠근다. */
private fun GeneralEditorState.draftSave(entry: GeneralEditorEntry): DraftSaveUiState =
    DraftSaveUiState(
        status =
            when {
                saving -> DraftSaveStatus.SAVING
                saveLocked -> DraftSaveStatus.SAVED
                else -> DraftSaveStatus.IDLE
            },
        canSave =
            entry is GeneralEditorEntry.Draft &&
                submission == null &&
                submittedForm == null &&
                inputsEnabled &&
                uploading.isEmpty() &&
                form.hasInput,
    )

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
    // 검토 중은 기다리면 되는 상태라 회색으로, 통과하지 못했거나 확인하지 못한 상태는 위험 색으로 알린다.
    val pending = submission?.status == "PENDING" && !state.acceptanceRecordFailed && !state.uncertainSubmission
    val accent = if (pending) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDanger
    Row(
        Modifier
            .padding(horizontal = ManyakTheme.spacing.gutter)
            .padding(bottom = ManyakTheme.spacing.compact)
            .fillMaxWidth()
            .background(
                if (pending) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.backgroundDangerSubtle,
                ManyakTheme.shapes.card,
            ).padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.component)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Icon(
            painterResource(R.drawable.ic_alert_circle),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(top = ManyakTheme.spacing.hairline).size(ManyakTheme.sizes.iconSmall),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
            Text(
                stringResource(title),
                style = ManyakTheme.typography.bodyMediumStrong,
                color = if (pending) ManyakTheme.colors.text else ManyakTheme.colors.textDanger,
            )
            reviewDescription(submission, editing)?.let {
                Text(
                    stringResource(it),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
            ReviewNotices(state)
            if (state.acceptanceRecordFailed) {
                ManyakTextButton(onClick = onRetry) {
                    Text(stringResource(DesignsystemR.string.common_retry), style = ManyakTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 칸 하나로 짚을 수 없는 검수 사유. 탭 이름을 붙여 점 목록으로 보인다. */
@Composable
private fun ReviewNotices(state: GeneralEditorState) {
    if (state.generalNotices.isEmpty()) return
    Column(
        Modifier.padding(top = ManyakTheme.spacing.inline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
    ) {
        state.generalNotices.forEach { notice ->
            val reason = notice.message.text ?: stringResource(checkNotNull(notice.message.message).resource())
            val text =
                notice.tab?.let {
                    stringResource(R.string.general_editor_tab_reason, stringResource(it.labelRes()), reason)
                } ?: reason
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                Text("•", style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
                Text(text, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
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

/** 닫을 때 무엇이 사라지는지에 따라 고르는 경고 문구. 제목, 설명, 머물기, 나가기 순서다. */
private data class GeneralExitCopy(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val stay: Int,
    @StringRes val leave: Int = R.string.general_editor_exit_leave,
)

private fun GeneralEditorState.exitCopy(editing: Boolean): GeneralExitCopy {
    val submitted = submission != null || submittedForm != null
    return when {
        editing ->
            GeneralExitCopy(
                R.string.general_editor_edit_exit_title,
                R.string.general_editor_edit_exit_description,
                R.string.general_editor_exit_close,
            )
        submitted && submittedForm != null && submittedForm != form ->
            GeneralExitCopy(
                R.string.general_editor_exit_edited_submission_title,
                R.string.general_editor_exit_edited_submission,
                R.string.general_editor_exit_close,
            )
        submitted ->
            GeneralExitCopy(
                R.string.general_editor_exit_title,
                R.string.general_editor_exit_submitted,
                R.string.general_editor_exit_close,
            )
        savedForm != null && savedForm != form ->
            GeneralExitCopy(
                R.string.create_unsaved_warning_title,
                R.string.create_unsaved_warning_description,
                R.string.general_editor_exit_close,
            )
        savedForm != null ->
            GeneralExitCopy(
                R.string.create_saved_exit_warning_title,
                R.string.create_saved_exit_warning_description,
                R.string.general_editor_exit_close,
            )
        form.hasInput ->
            GeneralExitCopy(
                R.string.create_unsaved_warning_title,
                R.string.create_unsaved_input_warning_description,
                R.string.general_editor_exit_close,
            )
        // 입력이 없어도 이미지를 올리는 중에 닫으면 올리던 이미지가 사라진다.
        else ->
            GeneralExitCopy(
                R.string.create_exit_warning_title,
                R.string.create_exit_warning_description,
                R.string.create_exit_warning_stay,
                R.string.create_exit_warning_leave,
            )
    }
}

@Composable
private fun GeneralExitDialog(
    state: GeneralEditorState,
    editing: Boolean,
    onIntent: (GeneralEditorIntent) -> Unit,
) {
    val copy = state.exitCopy(editing)
    ManyakDestructiveDialog(
        title = stringResource(copy.title),
        description = stringResource(copy.description),
        confirmLabel = stringResource(copy.leave),
        cancelLabel = stringResource(copy.stay),
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
        GeneralEditorMessage.DRAFT_FAILED -> R.string.general_editor_draft_failed
        GeneralEditorMessage.CHAT_FAILED -> R.string.general_editor_chat_failed
    }
