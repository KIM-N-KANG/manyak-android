package app.manyak.feature.studio

import androidx.lifecycle.viewModelScope
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.analytics.PendingCreationStage
import app.manyak.core.analytics.ReportSource
import app.manyak.core.analytics.StoryListSection
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.domain.story.resumePoint
import app.manyak.core.ui.mvi.MviViewModel
import app.manyak.core.ui.report.StoryReportAction
import app.manyak.core.ui.report.StoryReportChange
import app.manyak.core.ui.report.StoryReportController
import app.manyak.core.ui.report.StoryReportUiState
import app.manyak.core.ui.report.reduceReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 이어서 만들기 배너의 표시 정보. 레코드 존재만 확인하며 서버 조회는 하지 않는다(3-1). */
data class PendingCreationBanner(
    /** 완성 진행 중이면 "완성 중인 스토리가 있어요", 그 외에는 "만들고 있는 스토리가 있어요". */
    val isCompleting: Boolean,
    val resumePoint: CreationResumePoint,
)

data class StudioUiState(
    val isLoading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    val pendingBanner: PendingCreationBanner? = null,
    /** FAB 등 배너가 아닌 경로로 진입하려는데 임시 저장본이 있어 이어서/새로 만들기를 묻는 중. */
    val showResumeChoiceDialog: Boolean = false,
    /** 더보기·길게 누르기로 옵션 시트를 연 카드. null 이면 시트가 없다. */
    val optionsTarget: StorySummary? = null,
    /** 삭제 확인을 묻는 대상. null 이면 다이얼로그가 없다. */
    val deleteTarget: StorySummary? = null,
    val isDeleting: Boolean = false,
    /** 신고 시트. 대상은 옵션 시트를 연 카드다. */
    val report: StoryReportUiState = StoryReportUiState(),
    /** 신고 시트가 열려 있는 동안의 대상. 옵션 시트가 닫혀도 신고가 어느 스토리인지 남아야 한다. */
    val reportStoryId: String? = null,
)

sealed interface StudioIntent {
    /** 제작 퍼널 진입 시도(FAB). 진행 레코드가 있으면 다이얼로그로 묻는다. */
    data object CreateStory : StudioIntent

    /** 배너의 "이어서 만들기". */
    data object ResumeCreation : StudioIntent

    /** 다이얼로그의 "새로 만들기" — 레코드를 폐기하고 키워드 단계부터 시작한다. */
    data object StartNewCreation : StudioIntent

    data object DismissResumeChoiceDialog : StudioIntent

    /** 화면이 다시 보였다. 떠난 사이 바뀐 목록을 서버와 맞춘다. */
    data object ScreenShown : StudioIntent

    /** 목록 조회 실패 화면의 다시 시도. */
    data object Retry : StudioIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : StudioIntent

    /** 카드 더보기·길게 누르기 — 신고·삭제를 담은 옵션 시트를 연다. */
    data class OpenStoryOptions(
        val story: StorySummary,
    ) : StudioIntent

    data object CloseStoryOptions : StudioIntent

    /** 옵션 시트의 "삭제하기" — 바로 지우지 않고 확인을 묻는다. */
    data object RequestDeleteStory : StudioIntent

    /** 옵션 시트의 "신고하기" 이후 신고 시트 안의 동작. */
    data class Report(
        val action: StoryReportAction,
    ) : StudioIntent

    data object ConfirmDeleteStory : StudioIntent

    data object DismissDeleteDialog : StudioIntent
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
        val story: StorySummary?,
    ) : StudioEvent

    data class DeleteRequested(
        val story: StorySummary,
    ) : StudioEvent

    data class ReportTargetChanged(
        val storyId: String?,
    ) : StudioEvent

    data class Report(
        val change: StoryReportChange,
    ) : StudioEvent

    data object DeleteDialogDismissed : StudioEvent

    data object DeleteStarted : StudioEvent

    data class DeleteSucceeded(
        val storyId: String,
    ) : StudioEvent

    data object DeleteFailed : StudioEvent

    data class PendingCreationChanged(
        val banner: PendingCreationBanner?,
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
 * 제작 탭. 내가 만든 스토리 목록을 화면이 보일 때마다 조회하고, 진행 중인 제작 레코드를 구독한다.
 *
 * 목록은 서버가 소유하고 제작 완료로 늘어나므로, 화면을 떠났다 돌아오면 다시 읽어 맞춘다 —
 * 스토리를 완성하고 채팅으로 넘어갔다 돌아온 자리가 대표적이다. 이미 그릴 목록이 있는 갱신은
 * 골격 없이 조용히 바꿔 끼우고 실패해도 보고 있던 목록을 지우지 않는다.
 *
 * 목록을 보는 중에도 서버와 맞출 수 있게 당겨서 새로고침을 둔다. 화면 복귀 갱신과 달리 사용자가
 * 명시적으로 요청한 것이라 실패를 조용히 넘기지 않고 토스트로 알린다. 주기적 재조회는 두지 않는다.
 */
@HiltViewModel
class StudioViewModel
    @Inject
    constructor(
        private val pendingCreationStore: PendingStoryCreationStore,
        private val storyRepository: StoryRepository,
        private val analytics: Analytics,
    ) : MviViewModel<StudioIntent, StudioUiState, StudioEvent, StudioEffect>(StudioUiState()) {
        private var loadJob: Job? = null
        private var deleteJob: Job? = null

        /** 배너로 보여 준 레코드 단계. 같은 레코드가 다시 흘러와도 노출을 두 번 세지 않는다. */
        private var shownBannerStage: PendingCreationStage? = null

        /** 신고 절차는 상세·채팅방과 같아 :core:ui 의 컨트롤러가 소유한다. */
        private val report =
            StoryReportController(
                scope = viewModelScope,
                repository = storyRepository,
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
                pendingCreationStore.record.collect { record ->
                    val stage = record?.toStage()
                    if (stage != null && stage != shownBannerStage) {
                        analytics.track(AnalyticsEvent.ContinueBannerShown(stage))
                    }
                    shownBannerStage = stage
                    dispatchEvent(StudioEvent.PendingCreationChanged(record?.toBanner()))
                }
            }
        }

        override suspend fun handleIntent(intent: StudioIntent) {
            val state = uiState.value
            when (intent) {
                // 이미 그릴 목록이 있으면 갱신이 보이지 않아야 한다 — 골격이 다시 깔리면 복귀가 재진입처럼 보인다.
                StudioIntent.ScreenShown ->
                    load(if (state.stories.isEmpty()) LoadKind.Blocking else LoadKind.Silent)

                StudioIntent.Retry -> load(LoadKind.Blocking)

                StudioIntent.Refresh -> load(LoadKind.Refresh)

                is StudioIntent.OpenStoryOptions,
                StudioIntent.CloseStoryOptions,
                StudioIntent.RequestDeleteStory,
                StudioIntent.ConfirmDeleteStory,
                StudioIntent.DismissDeleteDialog,
                is StudioIntent.Report,
                -> handleCardIntent(intent, state)

                StudioIntent.CreateStory -> startCreation(state.pendingBanner)

                StudioIntent.ResumeCreation ->
                    state.pendingBanner?.let { banner ->
                        // 같은 Intent 가 배너와 재개 다이얼로그 두 곳에서 온다. 열려 있던 쪽이 출처다.
                        analytics.track(
                            if (state.showResumeChoiceDialog) {
                                AnalyticsEvent.ResumeDialogContinued
                            } else {
                                AnalyticsEvent.ContinueBannerClicked(
                                    shownBannerStage ?: PendingCreationStage.STORY_DRAFT,
                                )
                            },
                        )
                        dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                        dispatchEffect(StudioEffect.NavigateToResume(banner.resumePoint))
                    }

                StudioIntent.StartNewCreation -> {
                    analytics.track(AnalyticsEvent.ResumeDialogDiscarded)
                    // 레코드 폐기가 진입보다 먼저다 — 레코드가 남은 채 들어가면 재개로 복원된다.
                    pendingCreationStore.clear()
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                    dispatchEffect(StudioEffect.NavigateToCreate)
                }

                StudioIntent.DismissResumeChoiceDialog ->
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
            }
        }

        /** 카드 옵션 시트에서 갈라지는 동작 — 신고와 삭제. */
        private suspend fun handleCardIntent(
            intent: StudioIntent,
            state: StudioUiState,
        ) {
            when (intent) {
                is StudioIntent.OpenStoryOptions -> {
                    analytics.track(AnalyticsEvent.StoryOptionsOpened(intent.story.id))
                    dispatchEvent(StudioEvent.OptionsTargetChanged(intent.story))
                }

                StudioIntent.CloseStoryOptions -> dispatchEvent(StudioEvent.OptionsTargetChanged(null))

                // 삭제하기는 시트를 닫고 확인을 묻는다 — 시트 위에 다이얼로그가 겹치지 않는다.
                StudioIntent.RequestDeleteStory ->
                    state.optionsTarget?.let { story ->
                        dispatchEvent(StudioEvent.OptionsTargetChanged(null))
                        dispatchEvent(StudioEvent.DeleteRequested(story))
                    }

                StudioIntent.ConfirmDeleteStory -> confirmDelete(state.deleteTarget)

                StudioIntent.DismissDeleteDialog -> dismissDeleteDialog()

                is StudioIntent.Report -> handleReport(intent.action, state)

                else -> Unit
            }
        }

        /**
         * 신고 대상은 옵션 시트를 연 카드다. 열 때 대상을 따로 적어 두는 이유는 옵션 시트가 닫힌 뒤에도
         * 신고 시트가 어느 스토리를 보내는지 알아야 해서다.
         */
        private suspend fun handleReport(
            action: StoryReportAction,
            state: StudioUiState,
        ) {
            val storyId =
                if (action == StoryReportAction.Open) {
                    val target = state.optionsTarget?.id ?: return
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
                        is DomainResult.Success -> dispatchEvent(StudioEvent.StoriesLoaded(result.value))
                        is DomainResult.Failure -> reportLoadFailure(kind)
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

        /** FAB 등 배너가 아닌 경로의 진입. 임시 저장본이 있으면 바로 들어가지 않고 묻는다. */
        private suspend fun startCreation(pendingBanner: PendingCreationBanner?) {
            if (pendingBanner == null) {
                dispatchEffect(StudioEffect.NavigateToCreate)
            } else {
                analytics.track(AnalyticsEvent.ResumeDialogShown)
                dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = true))
            }
        }

        /** 다이얼로그가 이미 닫힌 뒤 확인이 도착하면 대상이 없다. */
        private fun confirmDelete(target: StorySummary?) {
            if (target != null) delete(target)
        }

        /** 삭제가 진행 중이면 닫지 않는다 — 결과가 정해진 뒤 상태 전이가 닫는다. */
        private suspend fun dismissDeleteDialog() {
            if (deleteJob?.isActive != true) dispatchEvent(StudioEvent.DeleteDialogDismissed)
        }

        private fun delete(target: StorySummary) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(StudioEvent.DeleteStarted)
                    when (storyRepository.deleteStory(target.id)) {
                        is DomainResult.Success -> {
                            analytics.track(AnalyticsEvent.StoryListStoryDeleted(target.id))
                            dispatchEvent(StudioEvent.DeleteSucceeded(target.id))
                            dispatchEffect(StudioEffect.ShowStoryDeleted)
                        }

                        is DomainResult.Failure -> {
                            dispatchEvent(StudioEvent.DeleteFailed)
                            dispatchEffect(StudioEffect.ShowStoryDeleteFailed)
                        }
                    }
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
                is StudioEvent.DeleteSucceeded,
                StudioEvent.DeleteFailed,
                -> reduceCardEvent(state, event)

                is StudioEvent.PendingCreationChanged ->
                    state.copy(
                        pendingBanner = event.banner,
                        // 다이얼로그가 열린 사이 레코드가 사라졌으면 물을 것도 없다.
                        showResumeChoiceDialog = state.showResumeChoiceDialog && event.banner != null,
                    )

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
        is StudioEvent.OptionsTargetChanged -> state.copy(optionsTarget = event.story)

        is StudioEvent.DeleteRequested -> state.copy(deleteTarget = event.story)

        is StudioEvent.ReportTargetChanged -> state.copy(reportStoryId = event.storyId)

        is StudioEvent.Report -> {
            val report = state.report.reduceReport(event.change)
            // 시트가 닫히면 대상도 함께 지운다 — 다음 신고가 지난 대상으로 나가면 안 된다.
            state.copy(report = report, reportStoryId = state.reportStoryId.takeIf { report.isSheetOpen })
        }

        StudioEvent.DeleteDialogDismissed -> state.copy(deleteTarget = null)

        StudioEvent.DeleteStarted -> state.copy(isDeleting = true)

        // 서버 재조회 대신 로컬 제거로 목록을 맞춘다 — 서버가 지운 것을 다시 물을 이유가 없다.
        is StudioEvent.DeleteSucceeded ->
            state.copy(
                isDeleting = false,
                deleteTarget = null,
                stories = state.stories.filterNot { story -> story.id == event.storyId },
            )

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
private fun PendingStoryCreation.toStage(): PendingCreationStage =
    when (this) {
        is PendingStoryCreation.KeywordDraft -> PendingCreationStage.KEYWORD_DRAFT
        is PendingStoryCreation.GeneratingStorylines -> PendingCreationStage.STORYLINE_GENERATION
        is PendingStoryCreation.CompletingStory -> PendingCreationStage.STORY_COMPLETION
        is PendingStoryCreation.Draft -> PendingCreationStage.STORY_DRAFT
    }

private fun PendingStoryCreation.toBanner(): PendingCreationBanner =
    PendingCreationBanner(
        isCompleting = this is PendingStoryCreation.CompletingStory,
        resumePoint = resumePoint(),
    )
