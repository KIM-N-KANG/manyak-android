package app.manyak.home.explore.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val isLoading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 새 순서를 받는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /**
     * 탭 진입으로 목록을 비우고 새 순서를 받을 때마다 오른다. 화면은 이 값을 스크롤 상태의 키로 써 새 순서를
     * 맨 위부터 보인다. 당겨서 새로고침은 칸 자리를 지킨 채 표지만 바꾸므로 올리지 않는다.
     */
    val entryVersion: Int = 0,
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface ExploreIntent {
    /**
     * 탐색 탭이 보였다. [request] 는 다른 탭에서 탐색으로 들어올 때마다 셸이 올리는 값이라, 처음 보는 값일 때만
     * 새로 섞는다. 상세에서 돌아오거나 구성이 바뀌어 다시 보낸 같은 값은 무시해 보던 순서와 위치를 남긴다.
     */
    data class Enter(
        val request: Int,
    ) : ExploreIntent

    data object Retry : ExploreIntent

    /** 당겨서 새로고침. 새로 섞은 첫 페이지로 바꾼다. */
    data object Refresh : ExploreIntent

    /** 목록 끝에 닿았거나 목록 끝의 다시 시도를 눌렀다. */
    data object LoadMore : ExploreIntent
}

sealed interface ExploreEvent {
    data object EntryStarted : ExploreEvent

    data object LoadStarted : ExploreEvent

    data object RefreshStarted : ExploreEvent

    data class Loaded(
        val page: StoryPage,
    ) : ExploreEvent

    data object LoadFailed : ExploreEvent

    data object RefreshFailed : ExploreEvent

    data object LoadMoreStarted : ExploreEvent

    data class MoreLoaded(
        val page: StoryPage,
    ) : ExploreEvent

    data object LoadMoreFailed : ExploreEvent
}

sealed interface ExploreEffect {
    data object ShowRefreshFailed : ExploreEffect
}

/**
 * 탐색 탭. 공개 스토리 전체를 서버가 섞은 순서로 끝까지 잇는다. 순서는 서버가 정하므로 화면에서 섞지 않는다.
 *
 * 첫 페이지는 cursor 없이 받아 새 순서로 시작하고 다음 페이지는 받은 nextCursor 로 같은 순서를 잇는다.
 * 같은 요청을 다시 보내면 순서가 바뀌므로 탭 진입과 당겨서 새로고침 말고는 저절로 다시 읽지 않는다.
 */
@HiltViewModel
class ExploreViewModel
    @Inject
    constructor(
        private val storyRepository: HomeRepository,
    ) : MviViewModel<ExploreIntent, ExploreUiState, ExploreEvent, ExploreEffect>(ExploreUiState()) {
        /** 첫 페이지와 다음 페이지를 합쳐 한 번에 하나만 둔다. 새 순서를 받으면 이전 순서의 다음 페이지가 섞이지 않게 취소한다. */
        private var pageJob: Job? = null

        /** 마지막으로 새로 섞은 탭 진입 값. 프로세스가 다시 시작되면 비어 있어 첫 진입처럼 읽는다. */
        private var handledEntry: Int? = null

        override suspend fun handleIntent(intent: ExploreIntent) {
            when (intent) {
                is ExploreIntent.Enter ->
                    if (intent.request != handledEntry) {
                        handledEntry = intent.request
                        loadFirstPage(ExploreEvent.EntryStarted)
                    }

                ExploreIntent.Retry -> if (pageJob?.isActive != true) loadFirstPage(ExploreEvent.LoadStarted)
                ExploreIntent.Refresh -> loadFirstPage(ExploreEvent.RefreshStarted)
                ExploreIntent.LoadMore -> loadNextPage()
            }
        }

        private fun loadFirstPage(started: ExploreEvent) {
            pageJob?.cancel()
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(started)
                    when (val result = storyRepository.exploreStories()) {
                        is DomainResult.Success -> dispatchEvent(ExploreEvent.Loaded(result.value))
                        is DomainResult.Failure ->
                            if (started == ExploreEvent.RefreshStarted) {
                                dispatchEvent(ExploreEvent.RefreshFailed)
                                dispatchEffect(ExploreEffect.ShowRefreshFailed)
                            } else {
                                dispatchEvent(ExploreEvent.LoadFailed)
                            }
                    }
                }
        }

        /**
         * 목록 끝은 스크롤 한 번에 여러 프레임 동안 참이라 진행 중인 조회가 있으면 무시한다.
         * 실패 뒤 자동 재요청을 멈추는 것은 화면이 맡는다. 같은 실패를 스크롤마다 반복하지 않는다.
         */
        private fun loadNextPage() {
            val cursor = uiState.value.nextCursor ?: return
            if (pageJob?.isActive == true) return
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(ExploreEvent.LoadMoreStarted)
                    when (val result = storyRepository.exploreStories(cursor)) {
                        is DomainResult.Success -> dispatchEvent(ExploreEvent.MoreLoaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(ExploreEvent.LoadMoreFailed)
                    }
                }
        }

        override fun reduce(
            state: ExploreUiState,
            event: ExploreEvent,
        ): ExploreUiState =
            when (event) {
                // 남은 목록과 스크롤을 버리고 새 순서를 맨 위부터 보인다.
                ExploreEvent.EntryStarted ->
                    ExploreUiState(entryVersion = state.entryVersion + 1)

                ExploreEvent.LoadStarted ->
                    state.copy(isLoading = true, loadFailed = false, isLoadingMore = false, loadMoreFailed = false)

                // 진행 중이던 다음 페이지는 취소됐다.
                ExploreEvent.RefreshStarted ->
                    state.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false)

                // 새로고침이면 앞서 이어 받은 다음 페이지까지 버리고 새 첫 페이지만 남긴다.
                is ExploreEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        stories = event.page.items.distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                ExploreEvent.LoadFailed ->
                    state.copy(isLoading = false, stories = emptyList(), nextCursor = null, loadFailed = true)

                // 새로고침 실패는 보고 있던 목록과 커서를 건드리지 않는다. 알림은 토스트가 맡는다.
                ExploreEvent.RefreshFailed -> state.copy(isRefreshing = false)

                ExploreEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)

                is ExploreEvent.MoreLoaded ->
                    state.copy(
                        stories = (state.stories + event.page.items).distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        isLoadingMore = false,
                    )

                // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                ExploreEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)
            }
    }
