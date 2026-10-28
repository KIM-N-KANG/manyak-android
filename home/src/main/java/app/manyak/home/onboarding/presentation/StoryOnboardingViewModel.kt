package app.manyak.home.onboarding.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryOnboardingDetailSource
import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoryOnboardingUiState(
    val stories: List<StorySummary> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** 가운데 카드의 오리지널 목록 안 순번. 채팅 시작하기가 이 스토리로 시작한다. */
    val selectedIndex: Int = 0,
    val isStartingChat: Boolean = false,
) {
    val selectedStory: StorySummary? get() = stories.getOrNull(selectedIndex)
}

sealed interface StoryOnboardingIntent {
    data object Retry : StoryOnboardingIntent

    /** 캐러셀의 가운데 카드가 바뀌었다. 스와이프 도중 절반을 넘긴 순간에 온다. */
    data class SelectStory(
        val index: Int,
    ) : StoryOnboardingIntent

    data class OpenStory(
        val index: Int,
        val source: StoryOnboardingDetailSource,
    ) : StoryOnboardingIntent

    data object StartChat : StoryOnboardingIntent

    data object CreateStory : StoryOnboardingIntent
}

sealed interface StoryOnboardingEvent {
    data object LoadStarted : StoryOnboardingEvent

    data class Loaded(
        val stories: List<StorySummary>,
    ) : StoryOnboardingEvent

    data object LoadFailed : StoryOnboardingEvent

    data class Selected(
        val index: Int,
    ) : StoryOnboardingEvent

    data object ChatStartRequested : StoryOnboardingEvent

    data object ChatStartReleased : StoryOnboardingEvent
}

sealed interface StoryOnboardingEffect {
    data class OpenStory(
        val storyId: String,
    ) : StoryOnboardingEffect

    /** 채팅이 만들어졌다. 앱이 온보딩을 끝내고 채팅방을 연다. */
    data class ChatStarted(
        val storyId: String,
        val chatId: String,
    ) : StoryOnboardingEffect

    data object OpenCreate : StoryOnboardingEffect

    data object ShowChatStartFailed : StoryOnboardingEffect
}

/**
 * 신규 가입 회원의 첫 화면. 오리지널을 넘겨 보다가 바로 채팅을 시작하거나 간편 제작으로 간다.
 *
 * 온보딩 완료 기록과 화면 전환은 앱이 맡는다 — 상세에서 시작한 채팅도 같은 완료라 이 ViewModel 밖에서 일어난다.
 */
@HiltViewModel
class StoryOnboardingViewModel
    @Inject
    constructor(
        private val storyRepository: HomeRepository,
        private val chatStarter: ChatStarter,
        private val analytics: Analytics,
    ) : MviViewModel<StoryOnboardingIntent, StoryOnboardingUiState, StoryOnboardingEvent, StoryOnboardingEffect>(
            StoryOnboardingUiState(),
        ) {
        private var loadJob: Job? = null
        private var chatJob: Job? = null
        private var dwellJob: Job? = null

        /** 이번 진입에서 가운데에 1초 이상 머문 스토리. 노출은 스토리마다 한 번만 센다. */
        private val browsed = mutableSetOf<String>()

        init {
            analytics.track(AnalyticsEvent.StoryOnboardingViewed)
            load()
        }

        override suspend fun handleIntent(intent: StoryOnboardingIntent) {
            val state = uiState.value
            when (intent) {
                StoryOnboardingIntent.Retry -> if (loadJob?.isActive != true) load()
                is StoryOnboardingIntent.SelectStory -> select(state, intent.index)
                is StoryOnboardingIntent.OpenStory -> openStory(state, intent)
                StoryOnboardingIntent.StartChat -> startChat(state)
                StoryOnboardingIntent.CreateStory -> {
                    analytics.track(AnalyticsEvent.StoryOnboardingCreateClicked(browsed.size))
                    dispatchEffect(StoryOnboardingEffect.OpenCreate)
                }
            }
        }

        override fun reduce(
            state: StoryOnboardingUiState,
            event: StoryOnboardingEvent,
        ): StoryOnboardingUiState =
            when (event) {
                StoryOnboardingEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)
                is StoryOnboardingEvent.Loaded ->
                    state.copy(stories = event.stories, isLoading = false, loadFailed = false, selectedIndex = 0)
                StoryOnboardingEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)
                is StoryOnboardingEvent.Selected -> state.copy(selectedIndex = event.index)
                StoryOnboardingEvent.ChatStartRequested -> state.copy(isStartingChat = true)
                StoryOnboardingEvent.ChatStartReleased -> state.copy(isStartingChat = false)
            }

        // ponytail: 첫 페이지(20편)만 읽는다. 오리지널이 그보다 많아지면 다음 커서까지 이어 읽는다.
        private fun load() {
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(StoryOnboardingEvent.LoadStarted)
                    val query = StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS)
                    when (val result = storyRepository.publicStories(query)) {
                        is DomainResult.Success -> dispatchEvent(StoryOnboardingEvent.Loaded(result.value.items))
                        is DomainResult.Failure -> {
                            analytics.track(AnalyticsEvent.StoryOnboardingLoadErrorShown)
                            dispatchEvent(StoryOnboardingEvent.LoadFailed)
                        }
                    }
                }
        }

        /** 스와이프로 스쳐 간 카드는 노출로 세지 않는다 — 가운데에 1초 머물러야 본 것으로 본다. */
        private suspend fun select(
            state: StoryOnboardingUiState,
            index: Int,
        ) {
            val story = state.stories.getOrNull(index) ?: return
            dispatchEvent(StoryOnboardingEvent.Selected(index))
            dwellJob?.cancel()
            dwellJob =
                viewModelScope.launch {
                    delay(IMPRESSION_DWELL_MILLIS)
                    if (browsed.add(story.id)) {
                        analytics.track(AnalyticsEvent.StoryOnboardingCardImpressed(story.id, index))
                    }
                }
        }

        private suspend fun openStory(
            state: StoryOnboardingUiState,
            intent: StoryOnboardingIntent.OpenStory,
        ) {
            val story = state.stories.getOrNull(intent.index) ?: return
            analytics.track(AnalyticsEvent.StoryOnboardingDetailClicked(story.id, intent.index, intent.source))
            dispatchEffect(StoryOnboardingEffect.OpenStory(story.id))
        }

        private suspend fun startChat(state: StoryOnboardingUiState) {
            val story = state.selectedStory ?: return
            if (chatJob?.isActive == true) return
            analytics.track(
                AnalyticsEvent.StoryOnboardingChatStartClicked(story.id, state.selectedIndex, browsed.size),
            )
            dispatchEvent(StoryOnboardingEvent.ChatStartRequested)
            chatJob =
                viewModelScope.launch {
                    when (val result = chatStarter.createChat(story.id)) {
                        // 잠금은 풀지 않는다 — 앱이 온보딩을 걷어내 이 화면은 돌아오지 않는다.
                        is DomainResult.Success ->
                            dispatchEffect(StoryOnboardingEffect.ChatStarted(story.id, result.value.id))

                        is DomainResult.Failure -> {
                            analytics.track(AnalyticsEvent.StoryOnboardingChatStartErrorShown(story.id))
                            dispatchEvent(StoryOnboardingEvent.ChatStartReleased)
                            dispatchEffect(StoryOnboardingEffect.ShowChatStartFailed)
                        }
                    }
                }
        }

        private companion object {
            const val IMPRESSION_DWELL_MILLIS = 1_000L
        }
    }
