package app.manyak.feature.studio

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.domain.story.resumePoint
import app.manyak.core.ui.mvi.MviViewModel
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
    val pendingBanner: PendingCreationBanner? = null,
    /** FAB 등 배너가 아닌 경로로 진입하려는데 임시 저장본이 있어 이어서/새로 만들기를 묻는 중. */
    val showResumeChoiceDialog: Boolean = false,
    /** 삭제 확인을 묻는 대상. null 이면 다이얼로그가 없다. */
    val deleteTarget: StorySummary? = null,
    val isDeleting: Boolean = false,
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

    /** 카드 더보기 메뉴의 "삭제하기" — 바로 지우지 않고 확인을 묻는다. */
    data class RequestDeleteStory(
        val story: StorySummary,
    ) : StudioIntent

    data object ConfirmDeleteStory : StudioIntent

    data object DismissDeleteDialog : StudioIntent
}

sealed interface StudioEvent {
    data object LoadStarted : StudioEvent

    data class StoriesLoaded(
        val stories: List<StorySummary>,
    ) : StudioEvent

    data object LoadFailed : StudioEvent

    data class DeleteRequested(
        val story: StorySummary,
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
}

/**
 * 제작 탭. 내가 만든 스토리 목록을 화면이 보일 때마다 조회하고, 진행 중인 제작 레코드를 구독한다.
 *
 * 목록은 서버가 소유하고 제작 완료로 늘어나므로, 화면을 떠났다 돌아오면 다시 읽어 맞춘다 —
 * 스토리를 완성하고 채팅으로 넘어갔다 돌아온 자리가 대표적이다. 이미 그릴 목록이 있는 갱신은
 * 골격 없이 조용히 바꿔 끼우고 실패해도 보고 있던 목록을 지우지 않는다.
 *
 * 목록을 주기적으로 다시 읽거나 당겨서 새로고침하지는 않는다 — 화면 복귀가 갱신 지점이고,
 * 실패했을 때 다시 부를 수단은 재시도로 충분하다.
 */
@HiltViewModel
class StudioViewModel
    @Inject
    constructor(
        private val pendingCreationStore: PendingStoryCreationStore,
        private val storyRepository: StoryRepository,
    ) : MviViewModel<StudioIntent, StudioUiState, StudioEvent, StudioEffect>(StudioUiState()) {
        private var loadJob: Job? = null
        private var deleteJob: Job? = null

        init {
            viewModelScope.launch {
                pendingCreationStore.record.collect { record ->
                    dispatchEvent(StudioEvent.PendingCreationChanged(record?.toBanner()))
                }
            }
        }

        override suspend fun handleIntent(intent: StudioIntent) {
            val state = uiState.value
            when (intent) {
                // 이미 그릴 목록이 있으면 갱신이 보이지 않아야 한다 — 골격이 다시 깔리면 복귀가 재진입처럼 보인다.
                StudioIntent.ScreenShown -> load(showProgress = state.stories.isEmpty())

                StudioIntent.Retry -> load(showProgress = true)

                is StudioIntent.RequestDeleteStory -> dispatchEvent(StudioEvent.DeleteRequested(intent.story))

                StudioIntent.ConfirmDeleteStory -> state.deleteTarget?.let { target -> delete(target) }

                StudioIntent.DismissDeleteDialog ->
                    // 삭제가 진행 중이면 닫지 않는다 — 결과가 정해진 뒤 상태 전이가 닫는다.
                    if (deleteJob?.isActive != true) dispatchEvent(StudioEvent.DeleteDialogDismissed)
                StudioIntent.CreateStory ->
                    if (state.pendingBanner == null) {
                        dispatchEffect(StudioEffect.NavigateToCreate)
                    } else {
                        dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = true))
                    }

                StudioIntent.ResumeCreation ->
                    state.pendingBanner?.let { banner ->
                        dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                        dispatchEffect(StudioEffect.NavigateToResume(banner.resumePoint))
                    }

                StudioIntent.StartNewCreation -> {
                    // 레코드 폐기가 진입보다 먼저다 — 레코드가 남은 채 들어가면 재개로 복원된다.
                    pendingCreationStore.clear()
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                    dispatchEffect(StudioEffect.NavigateToCreate)
                }

                StudioIntent.DismissResumeChoiceDialog ->
                    dispatchEvent(StudioEvent.ResumeChoiceDialogVisibleChanged(visible = false))
            }
        }

        /**
         * @param showProgress 그릴 목록이 없을 때만 true. 목록이 있는 갱신은 골격도 실패 화면도 띄우지
         *  않는다 — 보고 있던 목록이 사라지는 쪽이 갱신 실패보다 나쁘다.
         */
        private fun load(showProgress: Boolean) {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    if (showProgress) dispatchEvent(StudioEvent.LoadStarted)
                    when (val result = storyRepository.myStories()) {
                        is DomainResult.Success -> dispatchEvent(StudioEvent.StoriesLoaded(result.value))
                        is DomainResult.Failure -> if (showProgress) dispatchEvent(StudioEvent.LoadFailed)
                    }
                }
        }

        private fun delete(target: StorySummary) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(StudioEvent.DeleteStarted)
                    when (storyRepository.deleteStory(target.id)) {
                        is DomainResult.Success -> {
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
                StudioEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)

                is StudioEvent.StoriesLoaded ->
                    state.copy(isLoading = false, stories = event.stories, loadFailed = false)

                StudioEvent.LoadFailed -> state.copy(isLoading = false, stories = emptyList(), loadFailed = true)

                is StudioEvent.DeleteRequested -> state.copy(deleteTarget = event.story)

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

private fun PendingStoryCreation.toBanner(): PendingCreationBanner =
    PendingCreationBanner(
        isCompleting = this is PendingStoryCreation.CompletingStory,
        resumePoint = resumePoint(),
    )
