package app.manyak.home.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryListSection
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    /** 카드가 있는 섹션만 서버 순서대로 담는다. */
    val sections: List<HomeSection> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
)

sealed interface HomeIntent {
    data object Retry : HomeIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : HomeIntent
}

sealed interface HomeEvent {
    data object LoadStarted : HomeEvent

    data object RefreshStarted : HomeEvent

    data class Loaded(
        val sections: List<HomeSection>,
    ) : HomeEvent

    data object LoadFailed : HomeEvent

    data object RefreshFailed : HomeEvent
}

sealed interface HomeEffect {
    data object ShowRefreshFailed : HomeEffect
}

/**
 * 홈 탭. 관리자가 구성한 홈 섹션을 한 번에 읽는다. 섹션마다 카드는 최대 20장이라 페이지를 잇지 않는다.
 *
 * 탭을 다시 열 때마다 자동으로 다시 읽지 않는다. 목록을 보는 중에 서버와 맞출 수단으로 당겨서
 * 새로고침을 둔다. 골격을 다시 깔지 않고, 실패해도 보고 있던 섹션을 지우지 않은 채 토스트로만 알린다.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val storyRepository: HomeRepository,
        private val analytics: Analytics,
    ) : MviViewModel<HomeIntent, HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {
        private var loadJob: Job? = null

        init {
            analytics.track(AnalyticsEvent.StoryListViewed(StoryListSection.ORIGINAL))
            load(refresh = false)
        }

        override suspend fun handleIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.Retry -> if (loadJob?.isActive != true) load(refresh = false)
                HomeIntent.Refresh -> load(refresh = true)
            }
        }

        /** @param refresh 당겨서 새로고침이면 true. 실패를 화면이 아니라 토스트로 알린다. */
        private fun load(refresh: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(if (refresh) HomeEvent.RefreshStarted else HomeEvent.LoadStarted)
                    when (val result = storyRepository.homeSections()) {
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

        override fun reduce(
            state: HomeUiState,
            event: HomeEvent,
        ): HomeUiState =
            when (event) {
                HomeEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false, isRefreshing = false)

                HomeEvent.RefreshStarted -> state.copy(isRefreshing = true)

                // 섹션이 비어도 서버는 빈 배열로 준다. 숨김은 화면이 정하므로 여기서 거른다.
                is HomeEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        sections = event.sections.filter { section -> section.stories.isNotEmpty() },
                        loadFailed = false,
                        isRefreshing = false,
                    )

                HomeEvent.LoadFailed ->
                    state.copy(isLoading = false, sections = emptyList(), loadFailed = true, isRefreshing = false)

                // 새로고침 실패는 보고 있던 섹션을 건드리지 않는다. 알림은 토스트가 맡는다.
                HomeEvent.RefreshFailed -> state.copy(isRefreshing = false)
            }
    }
