package app.manyak.feature.home

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 카드가 그리는 필드가 [StorySummary] 와 정확히 같아 화면용 모델을 따로 두지 않는다 —
 * 같은 모양을 한 번 더 선언하면 필드가 늘 때 두 곳을 고쳐야 한다.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
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
        val stories: List<StorySummary>,
    ) : HomeEvent

    data object LoadFailed : HomeEvent

    data object RefreshFailed : HomeEvent
}

sealed interface HomeEffect {
    data object ShowRefreshFailed : HomeEffect
}

/**
 * 홈 탭. 마냑 공식 계정의 오리지널 스토리 목록을 진입 시 한 번 조회한다.
 *
 * 등록순 고정 목록이라 탭을 다시 열 때마다 바뀌지 않으므로 자동으로 다시 읽지 않는다. 대신 목록을
 * 보는 중에 서버와 맞출 수단으로 당겨서 새로고침을 둔다 — 골격을 다시 깔지 않고, 실패해도 보고
 * 있던 목록을 지우지 않은 채 토스트로만 알린다.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val storyRepository: StoryRepository,
    ) : MviViewModel<HomeIntent, HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {
        private var loadJob: Job? = null

        init {
            load(refresh = false)
        }

        override suspend fun handleIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.Retry -> load(refresh = false)
                HomeIntent.Refresh -> load(refresh = true)
            }
        }

        /**
         * @param refresh 당겨서 새로고침이면 true. 명시적 요청이라 진행 중인 조회를 기다리지 않고
         *  취소한 뒤 시작하며, 실패를 화면이 아니라 토스트로 알린다.
         */
        private fun load(refresh: Boolean) {
            if (refresh) {
                loadJob?.cancel()
            } else if (loadJob?.isActive == true) {
                return
            }
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(if (refresh) HomeEvent.RefreshStarted else HomeEvent.LoadStarted)
                    when (val result = storyRepository.originalStories()) {
                        is DomainResult.Success -> dispatchEvent(HomeEvent.Loaded(result.value))
                        is DomainResult.Failure ->
                            if (refresh) {
                                dispatchEvent(HomeEvent.RefreshFailed)
                                dispatchEffect(HomeEffect.ShowRefreshFailed)
                            } else {
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

                is HomeEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        stories = event.stories,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                HomeEvent.LoadFailed ->
                    state.copy(isLoading = false, stories = emptyList(), loadFailed = true, isRefreshing = false)

                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                HomeEvent.RefreshFailed -> state.copy(isRefreshing = false)
            }
    }
