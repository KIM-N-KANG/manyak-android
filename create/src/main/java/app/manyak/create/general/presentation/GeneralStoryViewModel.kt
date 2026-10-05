package app.manyak.create.general.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.auth.domain.AuthWork
import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.domain.buildGeneralStoryContent
import app.manyak.create.general.domain.buildGeneralStoryPatch
import app.manyak.create.general.domain.generalFieldValue
import app.manyak.create.general.domain.restoreGeneralStoryEditForm
import app.manyak.create.general.domain.restoreGeneralStoryForm
import app.manyak.create.general.domain.shouldDeleteGeneralThumbnail
import app.manyak.create.general.domain.validateGeneralStoryEdit
import app.manyak.create.general.domain.validateGeneralStoryForm
import app.manyak.create.general.entity.GeneralErrorReason
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryEditBase
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStorySaveResult
import app.manyak.create.general.entity.GeneralSubmission
import app.manyak.create.presentation.di.FunnelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = GeneralStoryViewModel.Factory::class)
class GeneralStoryViewModel
    @AssistedInject
    constructor(
        @Assisted val entry: GeneralEditorEntry,
        private val repository: GeneralStoryRepository,
        private val drafts: GeneralDraftStore,
        catalog: StoryCreationRepository,
        private val chatStarter: ChatStarter,
        private val gate: SessionGate,
        private val analytics: Analytics,
        @FunnelScope private val writeScope: CoroutineScope,
        private val clock: GeneralEditorClock,
    ) : MviViewModel<GeneralEditorIntent, GeneralEditorState, GeneralEditorEvent, GeneralEditorEffect>(
            GeneralEditorState(),
        ) {
        private var editBase: GeneralStoryEditBase? = null
        private var initialForm: GeneralStoryForm? = null
        private var lastSavedForm: GeneralStoryForm? = null
        private var writeJob: Job? = null
        private var saveJob: Job? = null
        private var loadJob: Job? = null
        private var acceptedId: String? = null
        private var acceptanceRecorded = true
        private var waitingReview: GeneralStoryEditor? = null
        private var reviewStartedAt: Long? = null
        private var validated = false
        private val touched = mutableSetOf<GeneralFieldTarget>()
        private val imagesToRelease = mutableMapOf<String, GeneralStoryImage>()
        private val generation = gate.currentGeneration
        private val isEdit: Boolean get() = entry is GeneralEditorEntry.Edit
        private val genres =
            GeneralGenreSearch(
                catalog,
                viewModelScope,
                state = { uiState.value.genreSearch },
                onState = { updateIfAlive(GeneralEditorEvent.GenreSearch(it)) },
                onCatalog = {
                    all,
                    featured,
                    failed,
                    ->
                    updateIfAlive(GeneralEditorEvent.Genres(all, failed, featured))
                },
            )
        private val uploads =
            GeneralEditorUploads(
                repository,
                viewModelScope,
                gate,
                generation,
                (entry as? GeneralEditorEntry.Edit)?.storyId,
                state = { uiState.value },
                event = ::updateIfAlive,
                apply = { changeForm(it, fromUpload = true) },
                failed = { message(GeneralEditorMessage.UPLOAD_FAILED) },
            )

        init {
            analytics.track(
                if (entry is GeneralEditorEntry.Edit) {
                    AnalyticsEvent.StoryEditViewed(entry.storyId)
                } else {
                    AnalyticsEvent.GeneralCreateViewed
                },
            )
            load()
            genres.loadCatalog()
        }

        @AssistedFactory
        interface Factory {
            fun create(entry: GeneralEditorEntry): GeneralStoryViewModel
        }

        override fun reduce(
            state: GeneralEditorState,
            event: GeneralEditorEvent,
        ): GeneralEditorState = reduceGeneralEditor(state, event)

        override suspend fun handleIntent(intent: GeneralEditorIntent) {
            if (!gate.isCurrentGeneration(generation)) return
            when (intent) {
                is GeneralEditorIntent.ChangeForm -> changeForm(intent.form)
                is GeneralEditorIntent.Blur -> {
                    touched += intent.target
                    dispatchEvent(GeneralEditorEvent.Errors(errors(uiState.value.form), false))
                }
                is GeneralEditorIntent.Upload -> uploads.upload(intent)
                GeneralEditorIntent.Submit -> submit()
                is GeneralEditorIntent.SaveDraft -> saveDraft(intent.showToast)
                GeneralEditorIntent.Retry -> retry()
                is GeneralEditorIntent.SearchGenres -> genres.query(intent.query)
                is GeneralEditorIntent.ExpandGenres -> genres.expand(intent.expanded)
                GeneralEditorIntent.RetryGenres -> genres.retry()
                GeneralEditorIntent.Close -> requestClose()
                GeneralEditorIntent.ConfirmClose -> {
                    dispatchEvent(GeneralEditorEvent.Exit(false))
                    dispatchEffect(GeneralEditorEffect.Close)
                }
                GeneralEditorIntent.DismissClose -> dispatchEvent(GeneralEditorEvent.Exit(false))
            }
        }

        private suspend fun requestClose() {
            val state = uiState.value
            val unchangedEdit = isEdit && state.form == initialForm
            // 새로 만들면서 입력한 것도, 저장하거나 등록을 요청한 것도 없으면 잃을 것이 없어 묻지 않고 나간다.
            val nothingToLose =
                !isEdit &&
                    !state.form.hasInput &&
                    state.savedForm == null &&
                    state.submission == null &&
                    state.submittedForm == null &&
                    acceptedId == null &&
                    state.uploading.isEmpty()
            if ((unchangedEdit || nothingToLose) && writeJob?.isActive != true) {
                dispatchEffect(GeneralEditorEffect.Close)
            } else {
                dispatchEvent(GeneralEditorEvent.Exit(true))
            }
        }

        private fun retry() {
            if (uiState.value.acceptanceRecordFailed && entry is GeneralEditorEntry.Draft) {
                if (writeJob?.isActive == true) return
                writeJob =
                    writeScope.launch {
                        withEditorAuth(Unit) { work -> recordAcceptance(work, entry.draftId, checkNotNull(acceptedId)) }
                        if (acceptanceRecorded) waitingReview?.let { reviewFinished(checkNotNull(acceptedId), it) }
                    }
            } else {
                load()
                genres.loadCatalog()
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    try {
                        withEditorAuth(Unit) { work ->
                            when (val current = entry) {
                                is GeneralEditorEntry.Draft -> loadDraft(current.draftId, work)
                                is GeneralEditorEntry.Submission ->
                                    loadRemote(
                                        repository.submission(current.submissionId),
                                    )
                                is GeneralEditorEntry.Edit -> loadRemote(repository.edit(current.storyId))
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        updateIfAlive(GeneralEditorEvent.LoadFailed)
                    }
                }
        }

        private suspend fun loadDraft(
            draftId: String,
            work: AuthWork,
        ) {
            val submissionId = drafts.acceptedSubmissionId(draftId)
            if (submissionId != null) {
                acceptedId = submissionId
                acceptanceRecorded = false
                recordAcceptance(work, draftId, submissionId)
                loadRemote(repository.submission(submissionId), restoringAccepted = true)
            } else {
                val stored = drafts.read(draftId)
                val form = stored?.form ?: uiState.value.form
                initialForm = form
                lastSavedForm = stored?.form
                retainImages(form)
                updateIfAlive(GeneralEditorEvent.Loaded(form, stored?.form))
            }
        }

        private suspend fun loadRemote(
            result: DomainResult<GeneralStoryEditor>,
            restoringAccepted: Boolean = false,
        ) {
            if (!gate.isCurrentGeneration(generation)) return
            when (result) {
                is DomainResult.Failure -> {
                    message(GeneralEditorMessage.LOAD_FAILED)
                    dispatchEffect(GeneralEditorEffect.Close)
                }
                is DomainResult.Success -> showRemote(result.value, restoringAccepted)
            }
        }

        private suspend fun showRemote(
            editor: GeneralStoryEditor,
            restoringAccepted: Boolean,
        ) {
            acceptedId = editor.submission?.id
            if (!isEdit && editor.submission?.status == "APPROVED") {
                editor.storyId?.let { dispatchEffect(GeneralEditorEffect.Approved(it)) }
                return
            }
            if (!isEdit && editor.submission?.status == "PENDING" && !restoringAccepted) {
                message(GeneralEditorMessage.PENDING)
                dispatchEffect(GeneralEditorEffect.Close)
                return
            }
            val edit =
                if (isEdit) {
                    restoreGeneralStoryEditForm(
                        editor.content,
                        editor.submission?.canResubmit == true,
                    )
                } else {
                    null
                }
            editBase = edit?.base
            val form = edit?.form ?: restoreGeneralStoryForm(editor.content)
            initialForm = form
            retainImages(form)
            if (restoringAccepted && editor.submission?.status == "PENDING") reviewStartedAt = clock.now()
            dispatchEvent(GeneralEditorEvent.Loaded(form, submission = editor.submission))
            editor.submission?.takeIf { it.canResubmit }?.let { showReview(it, form) }
        }

        private suspend fun changeForm(
            form: GeneralStoryForm,
            fromUpload: Boolean = false,
        ) {
            if (!uiState.value.inputsEnabled || !gate.isCurrentGeneration(generation)) return
            val previous = uiState.value.form
            if (!fromUpload) uploads.invalidateChanged(previous, form)
            retainImages(previous)
            retainImages(form)
            val changed =
                uiState.value.serverErrors.keys
                    .filter {
                        generalFieldValue(previous, it) != generalFieldValue(form, it)
                    }.toSet()
            dispatchEvent(GeneralEditorEvent.Form(form, errors(form), changed))
        }

        private fun errors(form: GeneralStoryForm): List<GeneralFieldError> {
            val allowed =
                uiState.value.genres.toSet().takeIf { it.isNotEmpty() }?.let {
                    it + if (isEdit) initialForm?.genres.orEmpty() else emptyList()
                }
            val all =
                editBase?.let { validateGeneralStoryEdit(form, it, allowed) }
                    ?: validateGeneralStoryForm(form, allowed)
            return if (validated) {
                all
            } else {
                all.filter {
                    it.target in touched &&
                        it.reason in setOf(GeneralErrorReason.TOO_SHORT, GeneralErrorReason.DUPLICATE)
                }
            }
        }

        private fun saveDraft(showToast: Boolean) {
            val draft = entry as? GeneralEditorEntry.Draft ?: return
            val state = uiState.value
            if (!canSaveGeneralDraft(state, acceptedId) || saveJob?.isActive == true) return
            val form = state.form
            saveJob =
                writeScope.launch {
                    updateIfAlive(GeneralEditorEvent.Saving(true))
                    var saved = false
                    try {
                        saved =
                            withEditorAuth(false) { work ->
                                gate.commit(work) { drafts.save(draft.draftId, form) }
                                    ?: false
                            }
                        if (saved) lastSavedForm = form
                        // 성공 안내는 헤더의 임시 저장 버튼이 맡는다. 간편 제작과 같은 버튼을 쓰기 위해서다.
                        if (showToast && !saved) message(GeneralEditorMessage.DRAFT_FAILED)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (showToast) message(GeneralEditorMessage.DRAFT_FAILED)
                    } finally {
                        withContext(NonCancellable) {
                            updateIfAlive(GeneralEditorEvent.Saving(false, saved, form.takeIf { saved }))
                        }
                    }
                    if (saved) {
                        viewModelScope.launch {
                            delay(DRAFT_SAVE_LOCK_MS)
                            updateIfAlive(GeneralEditorEvent.Saving(false))
                        }
                    }
                }
        }

        private suspend fun submit() {
            val state = uiState.value
            if (!state.inputsEnabled || state.uploading.isNotEmpty() || writeJob?.isActive == true) return
            if (isEdit && state.form == initialForm && editBase?.sendAll != true) {
                dispatchEffect(GeneralEditorEffect.Close)
                return
            }
            validated = true
            val errors = errors(state.form)
            dispatchEvent(GeneralEditorEvent.Errors(errors, true))
            if (errors.isNotEmpty()) return
            val requiresGenres =
                editBase?.let { it.sendAll || buildGeneralStoryPatch(state.form, it).genres != null } ?: true
            if (requiresGenres && (state.genresFailed || state.genres.isEmpty())) {
                genres.loadCatalog()
                message(GeneralEditorMessage.REGISTER_FAILED)
                return
            }
            dispatchEvent(GeneralEditorEvent.Busy(true))
            writeJob = writeScope.launch { performSubmit(state.form) }
        }

        private suspend fun performSubmit(form: GeneralStoryForm) {
            var requestStarted = false
            try {
                saveJob?.join()
                withTimeout(WRITE_TIMEOUT_MS) {
                    withEditorAuth(Unit) { work ->
                        requestStarted = true
                        val result =
                            if (entry is GeneralEditorEntry.Edit) {
                                updateStory(entry.storyId, form)
                            } else {
                                val content = buildGeneralStoryContent(form)
                                acceptedId?.let { repository.resubmit(it, content) } ?: repository.submit(content)
                            }
                        if (!gate.isCurrentGeneration(generation)) return@withEditorAuth
                        when (result) {
                            is DomainResult.Success -> handleSaved(work, result.value, form)
                            is DomainResult.Failure -> submissionFailed(result.error)
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                writeFailed(requestStarted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writeFailed(requestStarted)
            } finally {
                withContext(NonCancellable) { updateIfAlive(GeneralEditorEvent.Busy(false)) }
            }
        }

        private suspend fun writeFailed(requestStarted: Boolean) {
            when {
                acceptedId != null && !acceptanceRecorded -> {
                    updateIfAlive(GeneralEditorEvent.AcceptanceRecord(true))
                    message(GeneralEditorMessage.DRAFT_FAILED)
                }
                requestStarted && acceptedId == null && !isEdit -> markUncertain()
                else -> message(failureMessage())
            }
        }

        private suspend fun handleSaved(
            work: AuthWork,
            result: GeneralStorySaveResult,
            form: GeneralStoryForm,
        ) {
            when (result) {
                is GeneralStorySaveResult.Accepted -> {
                    acceptedId = result.submissionId
                    acceptanceRecorded = entry !is GeneralEditorEntry.Draft
                    reviewStartedAt = clock.now()
                    trackAccepted(result.submissionId, form)
                    updateIfAlive(
                        GeneralEditorEvent.Submission(GeneralSubmission(result.submissionId, "PENDING"), form),
                    )
                    message(GeneralEditorMessage.REVIEWING)
                    if (entry is GeneralEditorEntry.Draft) recordAcceptance(work, entry.draftId, result.submissionId)
                    if (acceptanceRecorded) waitingReview?.let { reviewFinished(result.submissionId, it) }
                }
                is GeneralStorySaveResult.Updated -> {
                    analytics.track(AnalyticsEvent.StoryEditCompleted((entry as GeneralEditorEntry.Edit).storyId))
                    message(GeneralEditorMessage.UPDATED)
                    effectIfAlive(GeneralEditorEffect.Close)
                }
            }
        }

        private suspend fun recordAcceptance(
            work: AuthWork,
            draftId: String,
            submissionId: String,
        ) {
            repeat(ACCEPTANCE_WRITE_ATTEMPTS) { attempt ->
                if (!gate.isCurrentGeneration(generation)) return
                acceptanceRecorded =
                    try {
                        gate.commit(work) { drafts.markAccepted(draftId, submissionId) } == true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                if (acceptanceRecorded) {
                    updateIfAlive(GeneralEditorEvent.AcceptanceRecord(false))
                    return
                }
                if (attempt < ACCEPTANCE_WRITE_ATTEMPTS - 1) delay(ACCEPTANCE_RETRY_MS)
            }
            updateIfAlive(GeneralEditorEvent.AcceptanceRecord(true))
            message(GeneralEditorMessage.DRAFT_FAILED)
        }

        private suspend fun updateStory(
            storyId: String,
            form: GeneralStoryForm,
        ): DomainResult<GeneralStorySaveResult> {
            var base = editBase ?: return DomainResult.Failure(DomainError.Unknown)
            if (shouldDeleteGeneralThumbnail(form, base)) {
                when (val deletion = repository.deleteCover(storyId)) {
                    is DomainResult.Failure -> return deletion
                    is DomainResult.Success -> {
                        base = base.copy(originalForm = base.originalForm.copy(cover = null))
                        editBase = base
                        initialForm = initialForm?.copy(cover = null)
                    }
                }
            }
            val patch = buildGeneralStoryPatch(form, base)
            return if (patch.isEmpty) {
                DomainResult.Success(GeneralStorySaveResult.Updated(GeneralStoryEditor(buildGeneralStoryContent(form))))
            } else {
                repository.update(storyId, patch)
            }
        }

        private fun trackAccepted(
            id: String,
            form: GeneralStoryForm,
        ) {
            if (entry is GeneralEditorEntry.Edit) {
                analytics.track(AnalyticsEvent.StoryEditCompleted(entry.storyId))
            } else {
                analytics.track(
                    AnalyticsEvent.GeneralCreateCompleted(
                        id,
                        form.startSettings.size,
                        form.startSettings.sumOf { it.endings.size },
                        form.mainEvents.size,
                        (if (form.cover != null) 1 else 0) + form.supporting.count { it.image != null },
                    ),
                )
            }
        }

        private suspend fun markUncertain() {
            updateIfAlive(GeneralEditorEvent.Uncertain(true))
            message(GeneralEditorMessage.SUBMIT_UNKNOWN)
        }

        private suspend fun submissionFailed(error: DomainError) {
            if (!isEdit) {
                analytics.track(
                    AnalyticsEvent.GeneralCreateRegisterErrorShown(
                        (error as? DomainError.Server)?.status ?: 0,
                    ),
                )
            }
            val server = error as? DomainError.Server
            if (!isEdit && acceptedId == null && isUncertainGeneralSubmission(error)) {
                markUncertain()
                return
            }
            val message =
                when {
                    server?.code == "IMAGES_TOO_LARGE" -> GeneralEditorMessage.IMAGES_TOO_LARGE
                    server?.code == "UPLOAD_NOT_FOUND" -> GeneralEditorMessage.UPLOAD_NOT_FOUND
                    server?.status == HTTP_CONFLICT -> GeneralEditorMessage.PENDING
                    else -> failureMessage()
                }
            message(message)
            if (server?.status == HTTP_CONFLICT && entry is GeneralEditorEntry.Edit) {
                val refreshed = repository.edit(entry.storyId)
                if (refreshed is DomainResult.Success) {
                    refreshed.value.submission?.let {
                        updateIfAlive(GeneralEditorEvent.Submission(it, uiState.value.form))
                    }
                }
            }
        }

        suspend fun observeReview() {
            uiState
                .map { it.submission?.takeIf { item -> item.status == "PENDING" }?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    val started = reviewStartedAt
                    if (id == null || started == null) return@collectLatest
                    pollGeneralReview(
                        startedAt = started,
                        now = clock::now,
                        fetch = {
                            withEditorAuth(
                                DomainResult.Failure(DomainError.Unauthorized),
                            ) { repository.submission(id) }
                        },
                        onResult = { reviewFinished(id, it) },
                        onTimeout = { reviewTimedOut(id) },
                    )
                }
        }

        private suspend fun reviewTimedOut(id: String) {
            if (!gate.isCurrentGeneration(generation)) return
            if (!isEdit) analytics.track(AnalyticsEvent.GeneralCreateReviewResultShown(id, "timeout"))
            message(if (isEdit) GeneralEditorMessage.TIMEOUT_EDIT else GeneralEditorMessage.TIMEOUT_CREATE)
            if (acceptanceRecorded) dispatchEffect(GeneralEditorEffect.Close)
        }

        private suspend fun reviewFinished(
            id: String,
            editor: GeneralStoryEditor,
        ) {
            if (!gate.isCurrentGeneration(generation)) return
            if (!acceptanceRecorded) {
                waitingReview = editor
                return
            }
            waitingReview = null
            val submission = editor.submission ?: return
            if (!isEdit) {
                analytics.track(
                    AnalyticsEvent.GeneralCreateReviewResultShown(id, submission.status.lowercase()),
                )
            }
            if (submission.status == "APPROVED") {
                showApproval(editor)
            } else {
                showReview(submission, uiState.value.submittedForm ?: uiState.value.form)
                editBase = editBase?.copy(sendAll = true)
                message(GeneralEditorMessage.REJECTED)
            }
        }

        private suspend fun showApproval(editor: GeneralStoryEditor) {
            if (isEdit) {
                message(GeneralEditorMessage.UPDATED)
                dispatchEffect(GeneralEditorEffect.Close)
            } else {
                editor.storyId?.let { storyId ->
                    val chat =
                        withEditorAuth(
                            DomainResult.Failure(DomainError.Unauthorized),
                        ) { chatStarter.createChat(storyId) }
                    if (chat is DomainResult.Failure) message(GeneralEditorMessage.CHAT_FAILED)
                    effectIfAlive(GeneralEditorEffect.Approved(storyId, (chat as? DomainResult.Success)?.value?.id))
                }
            }
        }

        private suspend fun showReview(
            submission: GeneralSubmission,
            form: GeneralStoryForm,
        ) {
            val errors = generalReviewErrors(submission, form)
            dispatchEvent(GeneralEditorEvent.Review(submission, errors.fields, emptyList(), errors.notices))
        }

        private suspend fun <T> withEditorAuth(
            blocked: T,
            block: suspend (AuthWork) -> T,
        ): T =
            if (!gate.isCurrentGeneration(generation)) {
                blocked
            } else {
                gate.withAuthWork(onBlocked = { blocked }) { work ->
                    if (!gate.isCurrentGeneration(generation)) blocked else block(work)
                }
            }

        private fun failureMessage() =
            if (isEdit) GeneralEditorMessage.UPDATE_FAILED else GeneralEditorMessage.REGISTER_FAILED

        private suspend fun message(message: GeneralEditorMessage) = effectIfAlive(GeneralEditorEffect.Message(message))

        private suspend fun updateIfAlive(event: GeneralEditorEvent) {
            if (viewModelScope.isActive && gate.isCurrentGeneration(generation)) dispatchEvent(event)
        }

        private suspend fun effectIfAlive(effect: GeneralEditorEffect) {
            if (viewModelScope.isActive && gate.isCurrentGeneration(generation)) dispatchEffect(effect)
        }

        private fun retainImages(form: GeneralStoryForm) {
            form.localImages().forEach { image -> image.localPath?.let { imagesToRelease[it] = image } }
        }

        override fun onCleared() {
            super.onCleared()
            val candidates = imagesToRelease.values.toList()
            writeScope.launch {
                saveJob?.join()
                writeJob?.join()
                val retained =
                    if (acceptedId != null && acceptanceRecorded) {
                        emptySet()
                    } else {
                        lastSavedForm
                            ?.localImages()
                            .orEmpty()
                            .mapNotNull { it.localPath }
                            .toSet()
                    }
                candidates.filterNot { it.localPath in retained }.forEach { repository.discardImage(it) }
            }
        }
    }

internal fun GeneralStoryForm.localImages(): List<GeneralStoryImage> =
    (listOfNotNull(cover) + supporting.mapNotNull { it.image }).filter { it.localPath != null }

internal fun canSaveGeneralDraft(
    state: GeneralEditorState,
    acceptedId: String?,
): Boolean {
    if (acceptedId != null || !state.inputsEnabled) return false
    if (state.saveLocked || state.uploading.isNotEmpty()) return false
    return state.form.hasInput
}

internal fun isUncertainGeneralSubmission(error: DomainError): Boolean =
    error == DomainError.Network ||
        error == DomainError.Serialization ||
        (error as? DomainError.Server)?.status?.let { it >= SERVER_ERROR_STATUS } == true

private const val WRITE_TIMEOUT_MS = 90_000L
private const val DRAFT_SAVE_LOCK_MS = 2_000L
private const val ACCEPTANCE_WRITE_ATTEMPTS = 3
private const val ACCEPTANCE_RETRY_MS = 1_000L
private const val SERVER_ERROR_STATUS = 500
private const val HTTP_CONFLICT = 409
