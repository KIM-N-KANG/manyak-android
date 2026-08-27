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
)

sealed interface HomeIntent {
    data object Retry : HomeIntent
}

sealed interface HomeEvent {
    data object LoadStarted : HomeEvent

    data class Loaded(
        val stories: List<StorySummary>,
    ) : HomeEvent

    data object LoadFailed : HomeEvent
}

/**
 * 홈 탭. 마냑 공식 계정의 오리지널 스토리 목록을 진입 시 한 번 조회한다.
 *
 * 목록을 주기적으로 다시 읽거나 당겨서 새로고침하지 않는다 — 등록순 고정 목록이라 탭을 다시
 * 열 때마다 바뀌지 않고, 실패했을 때 다시 부를 수단은 재시도로 충분하다.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val storyRepository: StoryRepository,
    ) : MviViewModel<HomeIntent, HomeUiState, HomeEvent, Nothing>(HomeUiState()) {
        private var loadJob: Job? = null

        init {
            load()
        }

        override suspend fun handleIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.Retry -> load()
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(HomeEvent.LoadStarted)
                    when (val result = storyRepository.originalStories()) {
                        is DomainResult.Success -> dispatchEvent(HomeEvent.Loaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(HomeEvent.LoadFailed)
                    }
                }
        }

        override fun reduce(
            state: HomeUiState,
            event: HomeEvent,
        ): HomeUiState =
            when (event) {
                HomeEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)
                is HomeEvent.Loaded -> state.copy(isLoading = false, stories = event.stories, loadFailed = false)
                HomeEvent.LoadFailed -> state.copy(isLoading = false, stories = emptyList(), loadFailed = true)
            }
    }
