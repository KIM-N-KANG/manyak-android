package app.manyak.home.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryPage
import app.manyak.home.search.domain.RecentSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorySearchUiState(
    /** 제출한 검색어. 없으면 검색 전 화면이다. 입력 중인 글자는 화면이 들고 제출할 때만 여기로 온다. */
    val query: String? = null,
    val recentTerms: List<String> = emptyList(),
    val featuredGenres: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val stories: List<StorySummary> = emptyList(),
    val loadFailed: Boolean = false,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** 새 검색어를 제출할 때마다 오른다. 화면은 이 값을 스크롤 상태의 키로 써 결과를 맨 위부터 보인다. */
    val searchVersion: Int = 0,
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface StorySearchIntent {
    /** 입력 제출, 최근 검색어나 대표 장르 선택. 앞뒤 공백을 뺀 값이 2자 미만이면 요청하지 않는다. */
    data class Submit(
        val term: String,
    ) : StorySearchIntent

    /** 입력을 지웠다. 검색 전 화면으로 돌아간다. */
    data object Clear : StorySearchIntent

    data object Retry : StorySearchIntent

    data object LoadMore : StorySearchIntent

    data class RemoveRecent(
        val term: String,
    ) : StorySearchIntent

    data object ClearRecent : StorySearchIntent
}

sealed interface StorySearchEvent {
    data class RecentChanged(
        val terms: List<String>,
    ) : StorySearchEvent

    data class GenresLoaded(
        val genres: List<String>,
    ) : StorySearchEvent

    data class SearchStarted(
        val query: String,
    ) : StorySearchEvent

    data object Cleared : StorySearchEvent

    data object RetryStarted : StorySearchEvent

    data class Loaded(
        val page: StoryPage,
    ) : StorySearchEvent

    data object LoadFailed : StorySearchEvent

    data object LoadMoreStarted : StorySearchEvent

    data class MoreLoaded(
        val page: StoryPage,
    ) : StorySearchEvent

    data object LoadMoreFailed : StorySearchEvent
}

sealed interface StorySearchEffect {
    data object ShowQueryTooShort : StorySearchEffect
}

/**
 * 스토리 검색 화면. 제출한 검색어로 공개 스토리를 관련도순으로 찾아 끝까지 잇는다.
 *
 * 서버는 받은 결과에서 비공개로 바뀐 스토리를 빼고 응답하므로 항목이 빈 페이지가 올 수 있다. 그때 다음 페이지가
 * 남아 있으면 이어 받아, 결과 없음은 다음 페이지가 없을 때만 보인다. 제출한 검색어는 저장 상태에 남겨 프로세스가
 * 다시 시작돼도 같은 검색을 다시 연다.
 */
@HiltViewModel
class StorySearchViewModel
    @Inject
    constructor(
        private val storyRepository: HomeRepository,
        private val recentSearches: RecentSearchRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : MviViewModel<StorySearchIntent, StorySearchUiState, StorySearchEvent, StorySearchEffect>(StorySearchUiState()) {
        /** 첫 페이지와 다음 페이지를 합쳐 한 번에 하나만 둔다. 다른 검색어를 제출하면 이전 검색의 조회를 취소한다. */
        private var pageJob: Job? = null

        init {
            viewModelScope.launch {
                recentSearches.terms.collect { terms -> dispatchEvent(StorySearchEvent.RecentChanged(terms)) }
            }
            viewModelScope.launch {
                // 대표 장르를 받지 못하면 그 구역만 숨긴다.
                val result = storyRepository.featuredGenres()
                if (result is DomainResult.Success) dispatchEvent(StorySearchEvent.GenresLoaded(result.value))
            }
            savedStateHandle.get<String>(QUERY_KEY)?.let { query -> search(query) }
        }

        override suspend fun handleIntent(intent: StorySearchIntent) {
            when (intent) {
                is StorySearchIntent.Submit -> submit(intent.term)
                StorySearchIntent.Clear -> {
                    pageJob?.cancel()
                    savedStateHandle.remove<String>(QUERY_KEY)
                    dispatchEvent(StorySearchEvent.Cleared)
                }

                StorySearchIntent.Retry -> uiState.value.query?.let { query -> loadFirstPage(query, retry = true) }
                StorySearchIntent.LoadMore -> loadNextPage()
                is StorySearchIntent.RemoveRecent -> recentSearches.remove(intent.term)
                StorySearchIntent.ClearRecent -> recentSearches.clear()
            }
        }

        private suspend fun submit(term: String) {
            val query = term.trim()
            if (query.length < QUERY_MIN_LENGTH) {
                dispatchEffect(StorySearchEffect.ShowQueryTooShort)
                return
            }
            recentSearches.add(query)
            search(query)
        }

        private fun search(query: String) {
            savedStateHandle[QUERY_KEY] = query
            loadFirstPage(query, retry = false)
        }

        private fun loadFirstPage(
            query: String,
            retry: Boolean,
        ) {
            pageJob?.cancel()
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(if (retry) StorySearchEvent.RetryStarted else StorySearchEvent.SearchStarted(query))
                    when (val result = fetchUntilItems(query, cursor = null)) {
                        is DomainResult.Success -> dispatchEvent(StorySearchEvent.Loaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(StorySearchEvent.LoadFailed)
                    }
                }
        }

        /** 목록 끝은 스크롤 한 번에 여러 프레임 동안 참이라 진행 중인 조회가 있으면 무시한다. */
        private fun loadNextPage() {
            val state = uiState.value
            val query = state.query ?: return
            val cursor = state.nextCursor ?: return
            if (pageJob?.isActive == true) return
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(StorySearchEvent.LoadMoreStarted)
                    when (val result = fetchUntilItems(query, cursor)) {
                        is DomainResult.Success -> dispatchEvent(StorySearchEvent.MoreLoaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(StorySearchEvent.LoadMoreFailed)
                    }
                }
        }

        /** 항목이 빈 페이지는 다음 페이지가 남아 있는 동안 이어 받는다. 빈 페이지 하나로 결과 없음이나 끝을 말하지 않는다. */
        private suspend fun fetchUntilItems(
            query: String,
            cursor: String?,
        ): DomainResult<StoryPage> {
            var result = storyRepository.searchStories(query, cursor)
            while (result is DomainResult.Success && result.value.items.isEmpty()) {
                val next = result.value.nextCursor ?: break
                result = storyRepository.searchStories(query, next)
            }
            return result
        }

        override fun reduce(
            state: StorySearchUiState,
            event: StorySearchEvent,
        ): StorySearchUiState =
            when (event) {
                is StorySearchEvent.RecentChanged -> state.copy(recentTerms = event.terms)
                is StorySearchEvent.GenresLoaded -> state.copy(featuredGenres = event.genres)

                // 이전 검색의 결과와 스크롤을 버리고 새 결과를 맨 위부터 보인다.
                is StorySearchEvent.SearchStarted ->
                    StorySearchUiState(
                        query = event.query,
                        recentTerms = state.recentTerms,
                        featuredGenres = state.featuredGenres,
                        isLoading = true,
                        searchVersion = state.searchVersion + 1,
                    )

                StorySearchEvent.Cleared ->
                    StorySearchUiState(
                        recentTerms = state.recentTerms,
                        featuredGenres = state.featuredGenres,
                        searchVersion = state.searchVersion,
                    )

                StorySearchEvent.RetryStarted -> state.copy(isLoading = true, loadFailed = false)

                is StorySearchEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        stories = event.page.items.distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        loadFailed = false,
                    )

                StorySearchEvent.LoadFailed ->
                    state.copy(isLoading = false, stories = emptyList(), nextCursor = null, loadFailed = true)

                StorySearchEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)

                is StorySearchEvent.MoreLoaded ->
                    state.copy(
                        stories = (state.stories + event.page.items).distinctBy { story -> story.id },
                        nextCursor = event.page.nextCursor,
                        isLoadingMore = false,
                    )

                // 이미 받은 결과는 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                StorySearchEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)
            }

        private companion object {
            const val QUERY_KEY = "query"
            const val QUERY_MIN_LENGTH = 2
        }
    }
