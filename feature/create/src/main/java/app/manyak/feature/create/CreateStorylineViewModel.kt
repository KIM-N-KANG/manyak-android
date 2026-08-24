package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineRating
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 생성 진행 중이거나, 결과(실패 시 빈 목록 포함)를 보여 주는 화면 콘텐츠. */
sealed interface StorylineContent {
    data object Generating : StorylineContent

    data class Loaded(
        val storylines: List<Storyline>,
    ) : StorylineContent
}

data class CreateStorylineUiState(
    val content: StorylineContent = StorylineContent.Generating,
    /** 생성·재생성 실패. 직전 결과가 남아 있으면 그대로 보여 주며 인라인 오류만 덧붙인다. */
    val hasGenerationError: Boolean = false,
    val activeIndex: Int = 0,
    /** 스토리라인 ID별 평가. 같은 평가를 다시 누르면 해제된다. */
    val ratings: Map<Long, StorylineRating> = emptyMap(),
) {
    val storylines: List<Storyline> get() = (content as? StorylineContent.Loaded)?.storylines.orEmpty()

    val activeStoryline: Storyline? get() = storylines.getOrNull(activeIndex)

    val activeRating: StorylineRating? get() = activeStoryline?.let { ratings[it.id] }
}

sealed interface CreateStorylineIntent {
    data class SelectStoryline(
        val index: Int,
    ) : CreateStorylineIntent

    data class ToggleRating(
        val rating: StorylineRating,
    ) : CreateStorylineIntent

    data object Regenerate : CreateStorylineIntent

    data object ConfirmSelection : CreateStorylineIntent
}

sealed interface CreateStorylineEvent {
    data class ActiveStorylineChanged(
        val index: Int,
    ) : CreateStorylineEvent

    /** 평가 토글·실패 롤백의 최종값 반영. null 은 평가 해제다. */
    data class RatingChanged(
        val storylineId: Long,
        val rating: StorylineRating?,
    ) : CreateStorylineEvent

    data class GenerationStateChanged(
        val generation: StorylineGenerationState,
    ) : CreateStorylineEvent
}

sealed interface CreateStorylineEffect {
    /** 활성 스토리라인 "선택하기" — 추가 정보 단계로 넘어간다. */
    data class NavigateToAdditionalInfo(
        val storylineIndex: Int,
    ) : CreateStorylineEffect

    /** 평가 동기화 실패 안내. 성공은 버튼 상태로 충분해 따로 알리지 않는다. */
    data object ShowRatingSyncFailed : CreateStorylineEffect
}

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
        private val storyCreationRepository: StoryCreationRepository,
    ) : MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, CreateStorylineEffect>(
            CreateStorylineUiState(),
        ) {
        private var regenerateJob: Job? = null

        /**
         * 평가 동기화 판정의 정본. UiState 반영은 이벤트 채널을 거쳐 한 박자 늦을 수 있어,
         * 원하는 값·서버 반영 값은 인텐트 처리 시점에 즉시 갱신되는 이 장부로 판정한다.
         */
        private val desiredRatings = mutableMapOf<Long, StorylineRating>()
        private val syncedRatings = mutableMapOf<Long, StorylineRating>()
        private val ratingSyncTimers = mutableMapOf<Long, Job>()
        private val ratingSyncInFlight = mutableSetOf<Long>()

        init {
            viewModelScope.launch {
                storylineGenerationStore.state.collect { generation ->
                    if (generation is StorylineGenerationState.Generated) resetRatingSync()
                    dispatchEvent(CreateStorylineEvent.GenerationStateChanged(generation))
                }
            }
        }

        override suspend fun handleIntent(intent: CreateStorylineIntent) {
            val state = uiState.value
            when (intent) {
                is CreateStorylineIntent.SelectStoryline ->
                    if (intent.index in state.storylines.indices) {
                        dispatchEvent(CreateStorylineEvent.ActiveStorylineChanged(intent.index))
                    }

                is CreateStorylineIntent.ToggleRating ->
                    state.activeStoryline?.let { storyline -> toggleRating(storyline.id, intent.rating) }

                CreateStorylineIntent.Regenerate -> startRegenerate(state)

                CreateStorylineIntent.ConfirmSelection ->
                    if (state.activeStoryline != null) {
                        dispatchEffect(CreateStorylineEffect.NavigateToAdditionalInfo(state.activeIndex))
                    }
            }
        }

        /** UI 에는 즉시 반영하고 서버 동기화는 디바운스한다. */
        private suspend fun toggleRating(
            storylineId: Long,
            rating: StorylineRating,
        ) {
            val next = if (desiredRatings[storylineId] == rating) null else rating
            desiredRatings.putOrRemove(storylineId, next)
            dispatchEvent(CreateStorylineEvent.RatingChanged(storylineId, next))
            scheduleRatingSync(storylineId)
        }

        private fun scheduleRatingSync(storylineId: Long) {
            ratingSyncTimers[storylineId]?.cancel()
            ratingSyncTimers[storylineId] =
                viewModelScope.launch {
                    delay(RATING_SYNC_DEBOUNCE_MS)
                    ratingSyncTimers.remove(storylineId)
                    launchRatingSync(storylineId)
                }
        }

        /**
         * 진행 중 요청은 완주시키고, 끝난 뒤 원하는 값과 서버 값이 갈리면 다시 동기화한다.
         * 진행 중 요청을 취소하면 서버 반영 여부를 알 수 없어 화면과 서버가 어긋난 채 남는다.
         */
        private fun launchRatingSync(storylineId: Long) {
            if (storylineId in ratingSyncInFlight) return
            ratingSyncInFlight += storylineId
            viewModelScope.launch {
                try {
                    syncRating(storylineId)
                } finally {
                    ratingSyncInFlight -= storylineId
                    if (desiredRatings[storylineId] != syncedRatings[storylineId]) {
                        launchRatingSync(storylineId)
                    }
                }
            }
        }

        private suspend fun syncRating(storylineId: Long) {
            val desired = desiredRatings[storylineId]
            val synced = syncedRatings[storylineId]
            if (desired == synced) return
            val result =
                if (desired == null) {
                    storyCreationRepository.clearStorylineRating(storylineId)
                } else {
                    storyCreationRepository.rateStoryline(storylineId, desired)
                }
            when (result) {
                is DomainResult.Success -> syncedRatings.putOrRemove(storylineId, desired)

                is DomainResult.Failure -> {
                    // 마지막으로 서버에 반영된 값으로 되돌리고 실패를 알린다.
                    desiredRatings.putOrRemove(storylineId, synced)
                    dispatchEvent(CreateStorylineEvent.RatingChanged(storylineId, synced))
                    dispatchEffect(CreateStorylineEffect.ShowRatingSyncFailed)
                }
            }
        }

        /** 새 생성 결과가 오면 이전 스토리라인의 평가 동기화는 의미가 없다. */
        private fun resetRatingSync() {
            ratingSyncTimers.values.forEach(Job::cancel)
            ratingSyncTimers.clear()
            desiredRatings.clear()
            syncedRatings.clear()
        }

        private fun startRegenerate(state: CreateStorylineUiState) {
            if (state.content is StorylineContent.Generating) return
            if (regenerateJob?.isActive == true) return
            // UNDISPATCHED — 버튼을 누른 즉시 스토어가 Generating 으로 바뀌어 중복 탭을 막는다.
            regenerateJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    storylineGenerationStore.regenerate()
                }
        }

        override fun reduce(
            state: CreateStorylineUiState,
            event: CreateStorylineEvent,
        ): CreateStorylineUiState =
            when (event) {
                is CreateStorylineEvent.ActiveStorylineChanged -> state.copy(activeIndex = event.index)
                is CreateStorylineEvent.RatingChanged ->
                    state.copy(
                        ratings =
                            if (event.rating == null) {
                                state.ratings - event.storylineId
                            } else {
                                state.ratings + (event.storylineId to event.rating)
                            },
                    )

                is CreateStorylineEvent.GenerationStateChanged -> reduceGeneration(state, event.generation)
            }

        private fun reduceGeneration(
            state: CreateStorylineUiState,
            generation: StorylineGenerationState,
        ): CreateStorylineUiState =
            when (generation) {
                StorylineGenerationState.Generating ->
                    state.copy(content = StorylineContent.Generating, hasGenerationError = false)

                is StorylineGenerationState.Generated ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.result.storylines),
                        hasGenerationError = false,
                        activeIndex = 0,
                        ratings = emptyMap(),
                    )

                // 재생성 실패면 직전 결과를 그대로 두고 인라인 오류만 켠다. 선택·평가 상태는
                // Generating 전이가 건드리지 않았으므로 직전 목록과 그대로 짝이 맞는다.
                is StorylineGenerationState.Failed ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.previousResult?.storylines.orEmpty()),
                        hasGenerationError = true,
                    )

                // 프로세스 재시작 복원 등으로 결과가 사라진 경우. 복구 대응이 정해지기 전까지는
                // 실패와 같은 화면으로 보여 준다("다시 만들기"는 직전 명령이 없어 동작하지 않는다).
                StorylineGenerationState.Idle ->
                    state.copy(content = StorylineContent.Loaded(emptyList()), hasGenerationError = true)
            }

        companion object {
            const val RATING_SYNC_DEBOUNCE_MS: Long = 300
        }
    }

private fun MutableMap<Long, StorylineRating>.putOrRemove(
    storylineId: Long,
    rating: StorylineRating?,
) {
    if (rating == null) remove(storylineId) else put(storylineId, rating)
}
