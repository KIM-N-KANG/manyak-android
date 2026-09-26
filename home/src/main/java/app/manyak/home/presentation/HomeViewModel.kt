package app.manyak.home.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryListSection
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import app.manyak.home.entity.StoryPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 카드가 그리는 필드가 [StorySummary] 와 정확히 같아 화면용 모델을 따로 두지 않는다 —
 * 같은 모양을 한 번 더 선언하면 필드가 늘 때 두 곳을 고쳐야 한다.
 */
data class HomeUiState(
    val query: StoryListQuery = StoryListQuery(),
    val isLoading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface HomeIntent {
    data object Retry : HomeIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : HomeIntent

    /** 목록 끝에 닿았거나 목록 끝의 다시 시도를 눌렀다. */
    data object LoadMore : HomeIntent

    data class SelectFilter(
        val filter: StoryListFilter,
    ) : HomeIntent

    data class SelectSort(
        val sort: StoryListSort,
    ) : HomeIntent
}

sealed interface HomeEvent {
    data class QueryChanged(
        val query: StoryListQuery,
    ) : HomeEvent

    data object LoadStarted : HomeEvent

    data object RefreshStarted : HomeEvent

    data class Loaded(
        val page: StoryPage,
    ) : HomeEvent

    data object LoadFailed : HomeEvent

    data object RefreshFailed : HomeEvent

    data object LoadMoreStarted : HomeEvent

    data class MoreLoaded(
        val page: StoryPage,
    ) : HomeEvent

    data object LoadMoreFailed : HomeEvent
}

sealed interface HomeEffect {
    data object ShowRefreshFailed : HomeEffect
}

/**
 * 홈 탭. 발행·공개된 스토리를 필터·정렬해 커서 페이지로 이어 읽는다.
 *
 * 탭을 다시 열 때마다 자동으로 다시 읽지 않는다. 목록을 보는 중에 서버와 맞출 수단으로 당겨서
 * 새로고침을 둔다 — 골격을 다시 깔지 않고, 실패해도 보고 있던 목록을 지우지 않은 채 토스트로만 알린다.
 *
 * 선택은 이 ViewModel 이 살아 있는 동안(홈 탭 수명) 유지하고 저장하지 않는다.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val storyRepository: HomeRepository,
        private val analytics: Analytics,
    ) : MviViewModel<HomeIntent, HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {
        /** 첫 페이지와 다음 페이지를 합쳐 한 번에 하나만 둔다. 선택을 바꾸면 이전 조건의 응답이 섞이지 않게 취소한다. */
        private var pageJob: Job? = null

        /**
         * 요청이 쓰는 조건. 상태의 [HomeUiState.query] 는 reduce 를 거쳐 늦게 바뀌므로, 선택을 연달아
         * 바꿨을 때 이전 조건으로 요청하지 않도록 여기서 바로 바꾼다.
         */
        private var query = StoryListQuery()

        init {
            analytics.track(AnalyticsEvent.StoryListViewed(StoryListSection.ORIGINAL))
            loadFirstPage(refresh = false)
        }

        override suspend fun handleIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.Retry -> if (pageJob?.isActive != true) loadFirstPage(refresh = false)
                HomeIntent.Refresh -> loadFirstPage(refresh = true)
                HomeIntent.LoadMore -> loadNextPage()
                is HomeIntent.SelectFilter -> select(query.copy(filter = intent.filter))
                is HomeIntent.SelectSort -> select(query.copy(sort = intent.sort))
            }
        }

        private suspend fun select(next: StoryListQuery) {
            if (next == query) return
            query = next
            pageJob?.cancel()
            dispatchEvent(HomeEvent.QueryChanged(next))
            loadFirstPage(refresh = false)
        }

        /**
         * @param refresh 당겨서 새로고침이면 true. 실패를 화면이 아니라 토스트로 알린다.
         */
        private fun loadFirstPage(refresh: Boolean) {
            pageJob?.cancel()
            val requested = query
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(if (refresh) HomeEvent.RefreshStarted else HomeEvent.LoadStarted)
                    when (val result = storyRepository.publicStories(requested)) {
                        is DomainResult.Success -> dispatchEvent(HomeEvent.Loaded(result.value))
                        is DomainResult.Failure ->
                            if (refresh) {
                                dispatchEvent(HomeEvent.RefreshFailed)
                                dispatchEffect(HomeEffect.ShowRefreshFailed)
                            } else {
                                analytics.track(AnalyticsEvent.StoryListLoadErrorShown(StoryListSection.ORIGINAL))
                                dispatchEvent(HomeEvent.LoadFailed)
                            }
                    }
                }
        }

        /**
         * 목록 끝은 스크롤 한 번에 여러 프레임 동안 참이라 진행 중인 조회가 있으면 무시한다.
         * 실패 뒤 자동 재요청을 멈추는 것은 화면이 맡는다 — 같은 실패를 스크롤마다 반복하지 않는다.
         */
        private fun loadNextPage() {
            val cursor = uiState.value.nextCursor ?: return
            if (pageJob?.isActive == true) return
            val requested = query
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(HomeEvent.LoadMoreStarted)
                    when (val result = storyRepository.publicStories(requested, cursor)) {
                        is DomainResult.Success -> dispatchEvent(HomeEvent.MoreLoaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(HomeEvent.LoadMoreFailed)
                    }
                }
        }

        override fun reduce(
            state: HomeUiState,
            event: HomeEvent,
        ): HomeUiState =
            when (event) {
                // 새 조건의 첫 페이지가 올 때까지 보던 목록을 남긴다 — 비웠다 채우면 칩을 누를 때마다 목록이 깜빡인다.
                // 커서는 비워 이전 조건의 다음 페이지를 잇지 않는다.
                is HomeEvent.QueryChanged ->
                    state.copy(
                        query = event.query,
                        isLoading = true,
                        nextCursor = null,
                        loadFailed = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        loadMoreFailed = false,
                    )

                HomeEvent.LoadStarted ->
                    state.copy(
                        isLoading = true,
                        loadFailed = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        loadMoreFailed = false,
                    )

                // 진행 중이던 다음 페이지는 취소됐다.
                HomeEvent.RefreshStarted ->
                    state.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false)

                is HomeEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        stories = event.page.items.distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                HomeEvent.LoadFailed ->
                    state.copy(
                        isLoading = false,
                        stories = emptyList(),
                        nextCursor = null,
                        loadFailed = true,
                        isRefreshing = false,
                    )

                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                HomeEvent.RefreshFailed -> state.copy(isRefreshing = false)

                HomeEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)

                // 페이지 사이에 순위가 바뀌면 이미 받은 스토리가 다시 온다. 먼저 받은 자리를 남긴다.
                is HomeEvent.MoreLoaded ->
                    state.copy(
                        stories = (state.stories + event.page.items).distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        isLoadingMore = false,
                    )

                // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                HomeEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)
            }
    }
