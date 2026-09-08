package app.manyak.studio.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.PendingCreationStage
import app.manyak.analytics.entity.ReportSource
import app.manyak.analytics.entity.StoryListSection
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.common.entity.story.StorySummary
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.report.domain.ReportRepository
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.StoryReportChange
import app.manyak.report.presentation.StoryReportController
import app.manyak.report.presentation.StoryReportUiState
import app.manyak.report.presentation.reduceReport
import app.manyak.studio.domain.StudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 옵션·삭제 확인의 대상이 되는 카드. 완성 중 요청은 옵션이 없어 여기 오지 않는다. */
sealed interface StudioCard {
    data class Story(
        val story: StorySummary,
    ) : StudioCard

    /** 편집 중 초안. 삭제는 로컬 초안만 지우고 서버를 부르지 않는다. */
    data object Draft : StudioCard

    /** 완성에 실패한 요청. 삭제는 그 요청 행만 지운다. */
    data class FailedRequest(
        val requestId: String,
    ) : StudioCard
}

data class StudioUiState(
    val isLoading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    /** 편집 중 초안. 레코드 존재만 확인하며 서버 조회는 하지 않는다. */
    val draft: CreationProgressSummary? = null,
    /** 제출 최신순 완성 요청. 완료된 요청은 목록에 같은 스토리가 실리면 사라진다. */
    val completionRequests: List<CompletionRequestSummary> = emptyList(),
    /** FAB 등 카드가 아닌 경로로 진입하려는데 초안이 있어 새로 만들기를 묻는 중. */
    val showResumeChoiceDialog: Boolean = false,
    /** 더보기·길게 누르기로 옵션 시트를 연 카드. null 이면 시트가 없다. */
    val optionsTarget: StudioCard? = null,
    /** 삭제 확인을 묻는 대상. null 이면 다이얼로그가 없다. */
    val deleteTarget: StudioCard? = null,
    val isDeleting: Boolean = false,
    /** 신고 시트. 대상은 옵션 시트를 연 카드다. */
    val report: StoryReportUiState = StoryReportUiState(),
    /** 신고 시트가 열려 있는 동안의 대상. 옵션 시트가 닫혀도 신고가 어느 스토리인지 남아야 한다. */
    val reportStoryId: String? = null,
) {
    /** 서버 목록과 무관하게 화면에 올릴 로컬 카드가 있는지. 있으면 빈 목록·실패 화면 대신 목록을 그린다. */
    val hasLocalCards: Boolean get() = draft != null || completionRequests.isNotEmpty()
}

sealed interface StudioIntent {
    /** 제작 퍼널 진입 시도(FAB). 초안이 있으면 다이얼로그로 묻는다. */
    data object CreateStory : StudioIntent

    /** 초안 카드의 "이어서 만들기". */
    data object ResumeCreation : StudioIntent

    /** 다이얼로그의 "새로 만들기" — 초안만 폐기하고 키워드 단계부터 시작한다. */
    data object StartNewCreation : StudioIntent

    data object DismissResumeChoiceDialog : StudioIntent

    /** 화면이 다시 보였다. 떠난 사이 바뀐 목록과 요청 상태를 서버와 맞춘다. */
    data object ScreenShown : StudioIntent

    /** 목록 조회 실패 화면의 다시 시도. */
    data object Retry : StudioIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : StudioIntent

    /** 카드 더보기·길게 누르기 — 옵션 시트를 연다. */
    data class OpenCardOptions(
        val card: StudioCard,
    ) : StudioIntent

    data object CloseCardOptions : StudioIntent

    /** 옵션 시트의 "삭제하기" — 바로 지우지 않고 확인을 묻는다. */
    data object RequestDelete : StudioIntent

    /** 옵션 시트의 "신고하기" 이후 신고 시트 안의 동작. */
    data class Report(
        val action: StoryReportAction,
    ) : StudioIntent

    data object ConfirmDelete : StudioIntent

    data object DismissDeleteDialog : StudioIntent

    /** 실패 카드의 다시 시도 — 같은 requestId 로 재전송한다. */
    data class RetryCompletion(
        val requestId: String,
    ) : StudioIntent
}

sealed interface StudioEvent {
    data object LoadStarted : StudioEvent

    data object RefreshStarted : StudioEvent

    data class StoriesLoaded(
        val stories: List<StorySummary>,
    ) : StudioEvent

    data object LoadFailed : StudioEvent

    data object RefreshFailed : StudioEvent

    data class OptionsTargetChanged(
        val card: StudioCard?,
    ) : StudioEvent

    data class DeleteRequested(
        val card: StudioCard,
    ) : StudioEvent

    data class ReportTargetChanged(
        val storyId: String?,
    ) : StudioEvent

    data class Report(
        val change: StoryReportChange,
    ) : StudioEvent

    data object DeleteDialogDismissed : StudioEvent

    data object DeleteStarted : StudioEvent

    data class StoryDeleteSucceeded(
        val storyId: String,
    ) : StudioEvent

    /** 로컬 카드 삭제 완료. 카드 자체는 저장소 흐름이 지운다. */
    data object LocalDeleteFinished : StudioEvent

    data object DeleteFailed : StudioEvent

    data class DraftChanged(
        val draft: CreationProgressSummary?,
    ) : StudioEvent

    data class CompletionRequestsChanged(
        val requests: List<CompletionRequestSummary>,
    ) : StudioEvent

    data class ResumeChoiceDialogVisibleChanged(
        val visible: Boolean,
    ) : StudioEvent
}

sealed interface StudioEffect {
    /** 새 생성으로 퍼널 진입 — 키워드 단계부터. */
    data object NavigateToCreate : StudioEffect

    /** 재개 진입 — 레코드 단계까지 퍼널 백스택을 쌓는다. */
    data class NavigateToResume(
        val resumePoint: CreationResumePoint,
    ) : StudioEffect

    data object ShowStoryDeleted : StudioEffect

    data object ShowStoryDeleteFailed : StudioEffect

    data object ShowRefreshFailed : StudioEffect

    data object ShowReportSubmitted : StudioEffect

    data object ShowReportFailed : StudioEffect
}

/**
 * 제작 탭. 내가 만든 스토리 목록을 화면이 보일 때마다 조회하고, 편집 초안과 완성 요청을 구독한다.
 * 목록·초안·요청·삭제·신고를 한 화면이 조정하므로 함수 수 상한을 넘긴다 — 나누면 상태 소유가 흩어진다.
 *
 * 목록은 서버가 소유하고 제작 완료로 늘어나므로, 화면을 떠났다 돌아오면 다시 읽어 맞춘다. 이미 그릴
 * 목록이 있는 갱신은 골격 없이 조용히 바꿔 끼우고 실패해도 보고 있던 목록을 지우지 않는다. 같은
 * 시점에 완성 요청의 서버 상태도 조회해, 완료된 요청은 목록에 실린 실제 스토리 카드로 바뀐다.
 *
 * 목록을 보는 중에도 서버와 맞출 수 있게 당겨서 새로고침을 둔다. 화면 복귀 갱신과 달리 사용자가
 * 명시적으로 요청한 것이라 실패를 조용히 넘기지 않고 토스트로 알린다. 주기적 재조회는 두지 않는다.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class StudioViewModel
    @Inject
    constructor(
        private val creationProgress: CreationProgressAccess,
        private val storyRepository: StudioRepository,
        private val analytics: Analytics,
        reportRepository: ReportRepository,
    ) : MviViewModel<StudioIntent, StudioUiState, StudioEvent, StudioEffect>(StudioUiState()) {
        private var loadJob: Job? = null
        private var deleteJob: Job? = null
        private var requestRefreshJob: Job? = null

        /** 초안 카드로 보여 준 레코드 단계. 같은 레코드가 다시 흘러와도 노출을 두 번 세지 않는다. */
        private var shownDraftStage: PendingCreationStage? = null

        /** 완료를 확인하고 목록을 다시 읽은 요청. 목록에 늦게 실려도 한 번만 다시 읽는다. */
        private val reloadedForRequests = mutableSetOf<String>()

        /** 목록에 실려 지우는 중인 완료 요청. 흐름이 같은 요청을 다시 흘려도 삭제를 두 번 부르지 않는다. */
        private val dismissingRequests = mutableSetOf<String>()

        /** 신고 절차는 상세·채팅방과 같아 공유 신고 컨트롤러가 소유한다. */
        private val report =
            StoryReportController(
                scope = viewModelScope,
                repository = reportRepository,
                analytics = analytics,
                source = ReportSource.STUDIO,
                emit = { change -> dispatchEvent(StudioEvent.Report(change)) },
                notify = { submitted ->
                    dispatchEffect(
                        if (submitted) StudioEffect.ShowReportSubmitted else StudioEffect.ShowReportFailed,
                    )
                },
            )

        init {
            analytics.track(AnalyticsEvent.StoryListViewed(StoryListSection.CREATED))
            viewModelScope.launch {
                creationProgress.progress.collect { draft ->
                    val stage = draft?.toStage()
                    if (stage != null && stage != shownDraftStage) {
                        analytics.track(AnalyticsEvent.ContinueBannerShown(stage))
                    }
                    shownDraftStage = stage
                    dispatchEvent(StudioEvent.DraftChanged(draft))
                }
            }
            viewModelScope.launch {
                creationProgress.completionRequests.collect { requests ->
                    dispatchEvent(StudioEvent.CompletionRequestsChanged(requests))
                    reconcileCompleted(requests, uiState.value.stories)
                }
            }
        }

        override suspend fun handleIntent(intent: StudioIntent) {
            val state = uiState.value
            when (intent) {
                // 이미 그릴 목록이 있으면 갱신이 보이지 않아야 한다 — 골격이 다시 깔리면 복귀가 재진입처럼 보인다.
                StudioIntent.ScreenShown -> {
                    refreshRequests()
                    load(if (state.stories.isEmpty()) LoadKind.Blocking else LoadKind.Silent)
                }

                StudioIntent.Retry -> {
                    refreshRequests()
                    load(LoadKind.Blocking)
                }

                StudioIntent.Refresh -> {
                    refreshRequests()
                    load(LoadKind.Refresh)
                }

                is StudioIntent.OpenCardOptions,
                StudioIntent.CloseCardOptions,
                StudioIntent.RequestDelete,
                StudioIntent.ConfirmDelete,
                StudioIntent.DismissDeleteDialog,
                is StudioIntent.Report,
                -> handleCardIntent(intent, state)

                StudioIntent.CreateStory -> startCreation(state.draft)

                StudioIntent.ResumeCreation ->
                    state.draft?.let { draft ->
                        // 같은 Intent 가 카드와 재개 다이얼로그 두 곳에서 온다. 열려 있던 쪽이 출처다.
                        analytics.track(
                            if (state.showResumeChoiceDialog) {
                                AnalyticsEvent.ResumeDialogContinued
                            } else {
                                AnalyticsEvent.ContinueBannerClicked(
                                    shownDraftStage ?: PendingCreationStage.STORY_DRAFT,
                                )
                            },
                        )
                        dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                        dispatchEffect(StudioEffect.NavigateToResume(draft.resumePoint))
                    }

                StudioIntent.StartNewCreation -> {
                    analytics.track(AnalyticsEvent.ResumeDialogDiscarded)
                    // 초안 폐기가 진입보다 먼저다 — 초안이 남은 채 들어가면 재개로 복원된다. 완성 요청은 남는다.
                    creationProgress.discard()
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                    dispatchEffect(StudioEffect.NavigateToCreate)
                }

                StudioIntent.DismissResumeChoiceDialog ->
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))

                is StudioIntent.RetryCompletion -> creationProgress.retryCompletionRequest(intent.requestId)
            }
        }

        /** 카드 옵션 시트에서 갈라지는 동작 — 신고와 삭제. */
        private suspend fun handleCardIntent(
            intent: StudioIntent,
            state: StudioUiState,
        ) {
            when (intent) {
                is StudioIntent.OpenCardOptions -> {
                    (intent.card as? StudioCard.Story)?.let {
                        analytics.track(
                            AnalyticsEvent.StoryOptionsOpened(it.story.id),
                        )
                    }
                    dispatchEvent(StudioEvent.OptionsTargetChanged(intent.card))
                }

                StudioIntent.CloseCardOptions -> dispatchEvent(StudioEvent.OptionsTargetChanged(null))

                // 삭제하기는 시트를 닫고 확인을 묻는다 — 시트 위에 다이얼로그가 겹치지 않는다.
                StudioIntent.RequestDelete ->
                    state.optionsTarget?.let { card ->
                        dispatchEvent(StudioEvent.OptionsTargetChanged(null))
                        dispatchEvent(StudioEvent.DeleteRequested(card))
                    }

                StudioIntent.ConfirmDelete -> confirmDelete(state.deleteTarget)

                StudioIntent.DismissDeleteDialog -> dismissDeleteDialog()

                is StudioIntent.Report -> handleReport(intent.action, state)

                else -> Unit
            }
        }

        /**
         * 신고 대상은 옵션 시트를 연 스토리 카드다. 열 때 대상을 따로 적어 두는 이유는 옵션 시트가 닫힌
         * 뒤에도 신고 시트가 어느 스토리를 보내는지 알아야 해서다.
         */
        private suspend fun handleReport(
            action: StoryReportAction,
            state: StudioUiState,
        ) {
            val storyId =
                if (action == StoryReportAction.Open) {
                    val target = (state.optionsTarget as? StudioCard.Story)?.story?.id ?: return
                    dispatchEvent(StudioEvent.OptionsTargetChanged(null))
                    dispatchEvent(StudioEvent.ReportTargetChanged(target))
                    target
                } else {
                    state.reportStoryId
                }
            report.handle(action, storyId, state.report)
        }

        private fun load(kind: LoadKind) {
            // 명시적 요청인 새로고침은 진행 중인 조회를 기다리지 않고 취소한 뒤 시작한다.
            if (kind == LoadKind.Refresh) {
                loadJob?.cancel()
            } else if (loadJob?.isActive == true) {
                return
            }
            loadJob =
                viewModelScope.launch {
                    when (kind) {
                        LoadKind.Blocking -> dispatchEvent(StudioEvent.LoadStarted)
                        LoadKind.Refresh -> dispatchEvent(StudioEvent.RefreshStarted)
                        LoadKind.Silent -> Unit
                    }
                    when (val result = storyRepository.myStories()) {
                        is DomainResult.Success -> {
                            dispatchEvent(StudioEvent.StoriesLoaded(result.value))
                            reconcileCompleted(uiState.value.completionRequests, result.value)
                        }

                        is DomainResult.Failure -> reportLoadFailure(kind)
                    }
                }
        }

        /** 요청 상태 조회는 목록 조회와 나란히 돈다. 이미 도는 조회가 있으면 겹쳐 시작하지 않는다. */
        private fun refreshRequests() {
            if (requestRefreshJob?.isActive == true) return
            requestRefreshJob = viewModelScope.launch { creationProgress.refreshCompletionRequests() }
        }

        /**
         * 완료된 요청을 실제 스토리 카드로 바꾼다. 목록에 그 스토리가 실렸으면 요청 행을 지우고, 아직
         * 없으면 목록을 한 번 조용히 다시 읽는다 — 그동안 요청 카드는 완료 상태로 남아 결과를 잃지 않는다.
         */
        private fun reconcileCompleted(
            requests: List<CompletionRequestSummary>,
            stories: List<StorySummary>,
        ) {
            val storyIds = stories.mapTo(mutableSetOf()) { it.id }
            requests
                .filter { it.status == CompletionRequestStatus.COMPLETED && it.storyId != null }
                .forEach { request ->
                    if (request.storyId in storyIds) {
                        if (dismissingRequests.add(request.requestId)) {
                            viewModelScope.launch {
                                if (!creationProgress.deleteCompletionRequest(request.requestId)) {
                                    dismissingRequests.remove(request.requestId)
                                }
                            }
                        }
                    } else if (reloadedForRequests.add(request.requestId)) {
                        load(LoadKind.Silent)
                    }
                }
        }

        private suspend fun reportLoadFailure(kind: LoadKind) {
            when (kind) {
                LoadKind.Blocking -> {
                    analytics.track(AnalyticsEvent.StoryListLoadErrorShown(StoryListSection.CREATED))
                    dispatchEvent(StudioEvent.LoadFailed)
                }

                LoadKind.Refresh -> {
                    dispatchEvent(StudioEvent.RefreshFailed)
                    dispatchEffect(StudioEffect.ShowRefreshFailed)
                }

                LoadKind.Silent -> Unit
            }
        }

        /** FAB 등 카드가 아닌 경로의 진입. 초안이 있으면 바로 들어가지 않고 묻는다. 완성 중 요청만 있으면 묻지 않는다. */
        private suspend fun startCreation(draft: CreationProgressSummary?) {
            if (draft == null) {
                dispatchEffect(StudioEffect.NavigateToCreate)
            } else {
                analytics.track(AnalyticsEvent.ResumeDialogShown)
                dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = true))
            }
        }

        /** 다이얼로그가 이미 닫힌 뒤 확인이 도착하면 대상이 없다. */
        private fun confirmDelete(target: StudioCard?) {
            if (target != null) delete(target)
        }

        /** 삭제가 진행 중이면 닫지 않는다 — 결과가 정해진 뒤 상태 전이가 닫는다. */
        private suspend fun dismissDeleteDialog() {
            if (deleteJob?.isActive != true) dispatchEvent(StudioEvent.DeleteDialogDismissed)
        }

        private fun delete(target: StudioCard) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(StudioEvent.DeleteStarted)
                    when (target) {
                        is StudioCard.Story -> deleteStory(target.story)

                        // 로컬 초안·요청은 서버 삭제 API 를 부르지 않는다. 실패하면 카드와 입력이 그대로 남는다.
                        StudioCard.Draft -> finishLocalDelete(creationProgress.discard())

                        is StudioCard.FailedRequest ->
                            finishLocalDelete(creationProgress.deleteCompletionRequest(target.requestId))
                    }
                }
        }

        private suspend fun deleteStory(story: StorySummary) {
            when (storyRepository.deleteStory(story.id)) {
                is DomainResult.Success -> {
                    analytics.track(AnalyticsEvent.StoryListStoryDeleted(story.id))
                    dispatchEvent(StudioEvent.StoryDeleteSucceeded(story.id))
                    dispatchEffect(StudioEffect.ShowStoryDeleted)
                }

                is DomainResult.Failure -> {
                    dispatchEvent(StudioEvent.DeleteFailed)
                    dispatchEffect(StudioEffect.ShowStoryDeleteFailed)
                }
            }
        }

        private suspend fun finishLocalDelete(deleted: Boolean) {
            if (deleted) {
                dispatchEvent(StudioEvent.LocalDeleteFinished)
            } else {
                dispatchEvent(StudioEvent.DeleteFailed)
                dispatchEffect(StudioEffect.ShowStoryDeleteFailed)
            }
        }

        override fun reduce(
            state: StudioUiState,
            event: StudioEvent,
        ): StudioUiState =
            when (event) {
                StudioEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false, isRefreshing = false)

                StudioEvent.RefreshStarted -> state.copy(isRefreshing = true)

                is StudioEvent.StoriesLoaded ->
                    state.copy(
                        isLoading = false,
                        stories = event.stories,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                StudioEvent.LoadFailed ->
                    state.copy(isLoading = false, stories = emptyList(), loadFailed = true, isRefreshing = false)

                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                StudioEvent.RefreshFailed -> state.copy(isRefreshing = false)

                is StudioEvent.OptionsTargetChanged,
                is StudioEvent.DeleteRequested,
                is StudioEvent.ReportTargetChanged,
                is StudioEvent.Report,
                StudioEvent.DeleteDialogDismissed,
                StudioEvent.DeleteStarted,
                is StudioEvent.StoryDeleteSucceeded,
                StudioEvent.LocalDeleteFinished,
                StudioEvent.DeleteFailed,
                -> reduceCardEvent(state, event)

                is StudioEvent.DraftChanged ->
                    state.copy(
                        draft = event.draft,
                        // 다이얼로그가 열린 사이 초안이 사라졌으면 물을 것도 없다.
                        showResumeChoiceDialog = state.showResumeChoiceDialog && event.draft != null,
                        optionsTarget =
                            state.optionsTarget.takeUnless {
                                it == StudioCard.Draft && event.draft == null
                            },
                        deleteTarget =
                            state.deleteTarget.takeUnless {
                                it == StudioCard.Draft && event.draft == null
                            },
                    )

                is StudioEvent.CompletionRequestsChanged -> state.copy(completionRequests = event.requests)

                is StudioEvent.ResumeChoiceDialogVisibleChanged ->
                    state.copy(showResumeChoiceDialog = event.visible)
            }
    }

/** 카드 옵션 시트에서 갈라지는 상태 전이 — 신고와 삭제. 순수 함수라 [MviViewModel.reduce] 와 같은 규칙을 따른다. */
private fun reduceCardEvent(
    state: StudioUiState,
    event: StudioEvent,
): StudioUiState =
    when (event) {
        is StudioEvent.OptionsTargetChanged -> state.copy(optionsTarget = event.card)

        is StudioEvent.DeleteRequested -> state.copy(deleteTarget = event.card)

        is StudioEvent.ReportTargetChanged -> state.copy(reportStoryId = event.storyId)

        is StudioEvent.Report -> {
            val report = state.report.reduceReport(event.change)
            // 시트가 닫히면 대상도 함께 지운다 — 다음 신고가 지난 대상으로 나가면 안 된다.
            state.copy(report = report, reportStoryId = state.reportStoryId.takeIf { report.isSheetOpen })
        }

        StudioEvent.DeleteDialogDismissed -> state.copy(deleteTarget = null)

        StudioEvent.DeleteStarted -> state.copy(isDeleting = true)

        // 서버 재조회 대신 로컬 제거로 목록을 맞춘다 — 서버가 지운 것을 다시 물을 이유가 없다.
        is StudioEvent.StoryDeleteSucceeded ->
            state.copy(
                isDeleting = false,
                deleteTarget = null,
                stories = state.stories.filterNot { story -> story.id == event.storyId },
            )

        StudioEvent.LocalDeleteFinished -> state.copy(isDeleting = false, deleteTarget = null)

        StudioEvent.DeleteFailed -> state.copy(isDeleting = false, deleteTarget = null)

        else -> state
    }

/** 목록 조회를 부른 자리. 진행을 어떻게 보이고 실패를 어떻게 알릴지가 여기서 갈린다. */
private enum class LoadKind {
    /** 첫 조회·재시도. 골격을 깔고 실패하면 재시도 화면으로 바꾼다. */
    Blocking,

    /** 화면 복귀. 보고 있던 목록을 건드리지 않고, 실패해도 아무것도 알리지 않는다. */
    Silent,

    /** 당겨서 새로고침. 당김 표시자로 진행을 알리고 실패는 토스트로 알린다. */
    Refresh,
}

/** 카탈로그의 `stage` 값. 웹의 진행 레코드 단계 이름과 맞춘다. */
private fun CreationProgressSummary.toStage(): PendingCreationStage =
    when (stage) {
        CreationStage.KEYWORD_DRAFT -> PendingCreationStage.KEYWORD_DRAFT
        CreationStage.STORYLINE_GENERATION -> PendingCreationStage.STORYLINE_GENERATION
        CreationStage.STORY_DRAFT -> PendingCreationStage.STORY_DRAFT
    }
